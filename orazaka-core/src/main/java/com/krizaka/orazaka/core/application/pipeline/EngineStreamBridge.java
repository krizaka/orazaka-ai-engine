package com.krizaka.orazaka.core.application.pipeline;

import com.krizaka.orazaka.core.application.engine.AbstractEngine;
import com.krizaka.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.orazaka.core.domain.event.ChatCompletedEvent;
import com.krizaka.orazaka.core.domain.model.CachedResponse;
import com.krizaka.orazaka.core.domain.model.chat.ChatCompletion;
import com.krizaka.orazaka.core.domain.model.chat.ChatRoute;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatResponse;
import com.krizaka.orazaka.core.domain.model.chat.TokenUsage;
import com.krizaka.orazaka.core.domain.ports.outbound.ChatInvocation;
import com.krizaka.orazaka.core.domain.ports.outbound.UserCredentialsProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Component streaming bridge for the Orazaka AI engine.
 *
 * <p>Creates a deferred {@link Flux} of {@link InternalChatResponse} chunks for real-time token
 * streaming. Handles tool execution fallback and post-completion event publishing.
 *
 * <p>The stream lifecycle:
 *
 * <ol>
 *   <li>Compiles the inference context via {@link EnginePipelineBridge#compileContext}
 *   <li>Opens a streaming connection to the resolved {@link ChatModel}
 *   <li>Falls back to tool-stripped streaming on {@link IllegalStateException} (sync or async)
 *   <li>Accumulates all chunks in a {@link StringBuilder} for post-processing
 *   <li>Publishes an {@link ChatCompletedEvent} with the full assembled text on completion
 * </ol>
 *
 * @see AbstractEngine#stream(InternalChatRequest)
 * @see EnginePipelineBridge
 */
@Component
public final class EngineStreamBridge {

  private static final Logger logger = LoggerFactory.getLogger(EngineStreamBridge.class);

  private final ChatInvocation chatInvocation;
  private final EnginePipelineBridge pipelineBridge;
  private final UserCredentialsProvider credentialsProvider;

  /** Constructs the streaming bridge. */
  public EngineStreamBridge(
      ChatInvocation chatInvocation,
      EnginePipelineBridge pipelineBridge,
      UserCredentialsProvider credentialsProvider) {
    this.chatInvocation = Objects.requireNonNull(chatInvocation, "ChatInvocation must not be null");
    this.pipelineBridge =
        Objects.requireNonNull(pipelineBridge, "EnginePipelineBridge must not be null");
    this.credentialsProvider =
        Objects.requireNonNull(credentialsProvider, "UserCredentialsProvider must not be null");
  }

  /**
   * Creates a deferred reactive stream of chat response chunks.
   *
   * <p>The {@code Flux.defer()} wrapper ensures that context compilation and model resolution
   * happen lazily at subscription time, not at assembly time.
   *
   * @param request The incoming internal chat request.
   * @param pipeline The orchestration pipeline (nullable — bypassed if null).
   * @param defaultProvider The default provider name.
   * @param interceptors Ordered interceptors for pre/post-processing.
   * @param eventPublisher Spring event publisher for {@link ChatCompletedEvent}.
   * @return A {@link Flux} emitting incremental {@link InternalChatResponse} chunks.
   */
  public Flux<InternalChatResponse> createStream(
      InternalChatRequest request,
      DynamicPipelineExecutor pipeline,
      String defaultProvider,
      List<PromptContextInterceptor> interceptors,
      ApplicationEventPublisher eventPublisher) {

    return Flux.defer(
        () -> {
          var context =
              pipelineBridge.compileContext(request, pipeline, defaultProvider, interceptors);
          if (context.cachedResponse() != null) {
            return servedFromCache(request, context, interceptors, eventPublisher);
          }
          var responseBuilder = new StringBuilder();
          long startTime = System.nanoTime();
          // Usage arrives once, on the last chunk of a stream — captured here so the completion
          // handler can settle the turn's hold against real tokens rather than an estimate.
          final AtomicReference<TokenUsage> tokenUsage = new AtomicReference<>(TokenUsage.none());

          ChatRoute route =
              ChatRoutes.resolve(
                  request.context().userId(), context.provider(), credentialsProvider);
          // Never the prompt, not even at DEBUG: a log keeps no data class (ADR-064).
          Flux<ChatCompletion> streamFlux = initStream(route, context);

          return streamFlux
              .onErrorResume(
                  RuntimeException.class,
                  ex -> {
                    logger.warn(
                        "Asynchronous tool execution activation failure, falling back to non-tool text inference stream.",
                        ex);
                    return chatInvocation.stream(context.toPrompt().withoutTools(), route);
                  })
              .map(
                  completion -> {
                    String chunk = completion.text();
                    if (chunk != null) {
                      responseBuilder.append(chunk);
                    }
                    captureTokenUsage(completion, tokenUsage);
                    return new InternalChatResponse(
                        chunk, context.conversationId(), Map.of("provider", context.provider()));
                  })
              .doOnComplete(
                  () -> {
                    String fullText = responseBuilder.toString();
                    interceptors.forEach(
                        i -> i.postProcess(request, context.promptText(), fullText));
                    Map<String, Object> metadata = buildCompletionMetadata(context, startTime);
                    var out =
                        new InternalChatResponse(
                            fullText,
                            context.conversationId(),
                            tokenUsage.get(),
                            Map.copyOf(metadata));
                    eventPublisher.publishEvent(
                        new ChatCompletedEvent(request, out, context.holdId()));
                  })
              .doFinally(signalType -> closeResources(context));
        });
  }

  /** A cached answer as a stream of one screened chunk, then the completion event (no usage). */
  private static Flux<InternalChatResponse> servedFromCache(
      InternalChatRequest request,
      EnginePipelineContext context,
      List<PromptContextInterceptor> interceptors,
      ApplicationEventPublisher eventPublisher) {
    var cached = context.cachedResponse();
    String text = ResponseScreen.apply(interceptors, context.promptContext(), cached.text());
    Map<String, Object> metadata =
        Map.of(
            "provider", CachedResponse.PROVIDER,
            "cache", cached.tier(),
            "cacheSimilarity", cached.similarity());
    var chunk = new InternalChatResponse(text, context.conversationId(), metadata);
    return Flux.just(chunk)
        .doOnComplete(
            () ->
                eventPublisher.publishEvent(
                    new ChatCompletedEvent(
                        request,
                        new InternalChatResponse(
                            text, context.conversationId(), TokenUsage.none(), metadata),
                        context.holdId())));
  }

  private Flux<ChatCompletion> initStream(ChatRoute route, EnginePipelineContext context) {
    try {
      return chatInvocation.stream(context.toPrompt(), route);
    } catch (RuntimeException e) {
      logger.warn(
          "Model '{}' stream failed tool execution activation, falling back to non-tool text inference stream.",
          context.provider(),
          e);
      return chatInvocation.stream(context.toPrompt().withoutTools(), route);
    }
  }

  /** Keeps the last usage a chunk carried; most chunks carry none and must not erase it. */
  private static void captureTokenUsage(
      ChatCompletion completion, AtomicReference<TokenUsage> tokenUsage) {
    if (completion != null && completion.usage().reported()) {
      tokenUsage.set(completion.usage());
    }
  }

  private static Map<String, Object> buildCompletionMetadata(
      EnginePipelineContext context, long startTime) {
    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("provider", context.provider());
    metadata.put("durationMs", durationMs);
    return metadata;
  }

  private static void closeResources(EnginePipelineContext context) {
    if (context.closeables() == null) {
      return;
    }
    for (AutoCloseable c : context.closeables()) {
      try {
        c.close();
      } catch (Exception e) {
        logger.error("Failed to close dynamic MCP client", e);
      }
    }
  }
}
