package com.krizaka.orazaka.core.application.pipeline;

import com.krizaka.orazaka.core.application.engine.AbstractEngine;
import com.krizaka.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.orazaka.core.application.routing.DynamicChatModelFactory;
import com.krizaka.orazaka.core.application.routing.ProviderClassifier;
import com.krizaka.orazaka.core.domain.event.ChatCompletedEvent;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatResponse;
import com.krizaka.orazaka.core.domain.model.chat.TokenUsage;
import com.krizaka.orazaka.core.domain.ports.outbound.UserCredentialsProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
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

  private final ChatModel chatModel;
  private final EnginePipelineBridge pipelineBridge;
  private final UserCredentialsProvider credentialsProvider;
  private final DynamicChatModelFactory modelFactory;

  /** Constructs the streaming bridge. */
  public EngineStreamBridge(
      ChatModel chatModel,
      EnginePipelineBridge pipelineBridge,
      UserCredentialsProvider credentialsProvider,
      DynamicChatModelFactory modelFactory) {
    this.chatModel = Objects.requireNonNull(chatModel, "ChatModel must not be null");
    this.pipelineBridge =
        Objects.requireNonNull(pipelineBridge, "EnginePipelineBridge must not be null");
    this.credentialsProvider =
        Objects.requireNonNull(credentialsProvider, "UserCredentialsProvider must not be null");
    this.modelFactory =
        Objects.requireNonNull(modelFactory, "DynamicChatModelFactory must not be null");
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
          var responseBuilder = new StringBuilder();
          long startTime = System.nanoTime();
          // Usage arrives once, on the last chunk of a stream — captured here so the completion
          // handler can settle the turn's hold against real tokens rather than an estimate.
          final AtomicReference<TokenUsage> tokenUsage = new AtomicReference<>(TokenUsage.none());

          ChatModel activeModel = resolveActiveModel(request, context);
          logger.debug(
              "OLLAMA_DEBUG: context.toPrompt().getOptions() = {}",
              context.toPrompt().getOptions());
          logger.debug(
              "OLLAMA_DEBUG: context.toPrompt().getOptions().getClass() = {}",
              context.toPrompt().getOptions() != null
                  ? context.toPrompt().getOptions().getClass().getName()
                  : "null");
          logger.debug(
              "OLLAMA_DEBUG: context.toPrompt().getInstructions() = {}",
              context.toPrompt().getInstructions());

          Flux<ChatResponse> streamFlux = initStream(activeModel, context);

          final ChatModel finalActiveModel = activeModel;
          return streamFlux
              .onErrorResume(
                  RuntimeException.class,
                  ex -> {
                    logger.warn(
                        "Asynchronous tool execution activation failure, falling back to non-tool text inference stream.",
                        ex);
                    return finalActiveModel.stream(
                        EnginePipelineBridge.removeTools(context.toPrompt()));
                  })
              .map(
                  chatResponse -> {
                    String chunk = extractChunkText(chatResponse);
                    if (chunk != null) responseBuilder.append(chunk);
                    captureTokenUsage(chatResponse, tokenUsage);
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

  private ChatModel resolveActiveModel(InternalChatRequest request, EnginePipelineContext context) {
    String resolvedProvider = context.provider();
    if (!ProviderClassifier.isCommercial(resolvedProvider)) {
      return this.chatModel;
    }
    String userId = request.context().userId();
    var apiKeyOpt = credentialsProvider.getDecryptedApiKey(userId, resolvedProvider);
    if (apiKeyOpt.isEmpty()) {
      logger.warn(
          "No API key found for user {} and provider {}, falling back to local model.",
          userId,
          resolvedProvider);
      return this.chatModel;
    }
    String modelName = extractModelName(context);
    return modelFactory.createChatModel(resolvedProvider, modelName, apiKeyOpt.get());
  }

  private static String extractModelName(EnginePipelineContext context) {
    var options = context.toPrompt().getOptions();
    if (options instanceof OpenAiChatOptions openaiOpts) return openaiOpts.getModel();
    if (options instanceof AnthropicChatOptions anthropicOpts) return anthropicOpts.getModel();
    if (options instanceof GoogleGenAiChatOptions geminiOpts) return geminiOpts.getModel();
    return null;
  }

  private static Flux<ChatResponse> initStream(ChatModel model, EnginePipelineContext context) {
    try {
      return model.stream(context.toPrompt());
    } catch (RuntimeException e) {
      logger.warn(
          "Model '{}' stream failed tool execution activation, falling back to non-tool text inference stream.",
          context.provider(),
          e);
      return model.stream(EnginePipelineBridge.removeTools(context.toPrompt()));
    }
  }

  private static String extractChunkText(ChatResponse chatResponse) {
    if (chatResponse == null
        || chatResponse.getResult() == null
        || chatResponse.getResult().getOutput() == null) {
      return "";
    }
    String text = chatResponse.getResult().getOutput().getText();
    return text != null ? text : "";
  }

  /** Keeps the last usage a chunk carried; most chunks carry none and must not erase it. */
  private static void captureTokenUsage(
      ChatResponse chatResponse, AtomicReference<TokenUsage> tokenUsage) {
    if (chatResponse == null || chatResponse.getMetadata() == null) {
      return;
    }
    var usage = chatResponse.getMetadata().getUsage();
    if (usage == null) {
      return;
    }
    tokenUsage.set(
        TokenUsage.reported(
            usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens()));
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
    if (context.closeables() == null) return;
    for (AutoCloseable c : context.closeables()) {
      try {
        c.close();
      } catch (Exception e) {
        logger.error("Failed to close dynamic MCP client", e);
      }
    }
  }
}
