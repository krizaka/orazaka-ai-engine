package com.orazaka.core.application.pipeline;

import com.orazaka.core.application.engine.AbstractEngine;
import com.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.orazaka.core.application.routing.ChatOptionsResolver;
import com.orazaka.core.application.routing.ProviderClassifier;
import com.orazaka.core.domain.model.PromptContext;
import com.orazaka.core.domain.model.chat.InternalChatRequest;
import com.orazaka.core.domain.ports.outbound.ModelCatalogProvider;
import com.orazaka.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.orazaka.core.domain.ports.outbound.ToolRegistry;
import com.orazaka.core.domain.ports.outbound.UserMcpServerProvider;
import com.orazaka.core.infrastructure.support.CoreException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * Component bridge that compiles a complete inference context from a raw chat request.
 *
 * <p>Orchestrates the pre-inference pipeline by delegating to:
 *
 * <ul>
 *   <li>{@link DynamicPipelineExecutor} — refiner → router → context resolvers
 *   <li>{@link ChatOptionsResolver} — provider-specific options mapping
 *   <li>{@link McpClientFactory} — MCP tool callback resolution
 *   <li>{@link MessageCompiler} — chat message history compilation
 * </ul>
 *
 * @see EnginePipelineContext
 * @see AbstractEngine
 */
@Component
public final class EnginePipelineBridge {

  private final ModelCatalogProvider modelCatalogProvider;
  private final PlatformMcpServerProvider platformMcpServerProvider;
  private final UserMcpServerProvider userMcpServerProvider;
  private final ToolRegistry toolRegistry;

  /**
   * Constructs the bridge.
   *
   * @param modelCatalogProvider The outbound model catalog provider.
   * @param platformMcpServerProvider The platform-wide MCP provider.
   * @param userMcpServerProvider The user private MCP provider.
   * @param toolRegistry The local tools registry.
   */
  public EnginePipelineBridge(
      ModelCatalogProvider modelCatalogProvider,
      PlatformMcpServerProvider platformMcpServerProvider,
      UserMcpServerProvider userMcpServerProvider,
      ToolRegistry toolRegistry) {
    this.modelCatalogProvider =
        Objects.requireNonNull(modelCatalogProvider, "ModelCatalogProvider must not be null");
    this.platformMcpServerProvider = platformMcpServerProvider;
    this.userMcpServerProvider = userMcpServerProvider;
    this.toolRegistry = toolRegistry;
  }

  /**
   * Compiles a full inference context from the raw request through the pipeline.
   *
   * @param request The incoming internal chat request.
   * @param pipeline The dynamic pipeline executor (nullable — bypassed if null).
   * @param defaultProvider The default provider name.
   * @param interceptors Ordered interceptors for pre-processing the prompt and options.
   * @return A fully compiled {@link EnginePipelineContext} ready for model invocation.
   * @throws CoreException If the resolved provider is empty or target model is missing.
   */
  public EnginePipelineContext compileContext(
      InternalChatRequest request,
      DynamicPipelineExecutor pipeline,
      String defaultProvider,
      List<PromptContextInterceptor> interceptors) {

    String originalPrompt = request.prompt();
    String cleanedRawQuery = originalPrompt != null ? originalPrompt : "";

    // Extract Base64 media with size validation (max 10 MB)
    Base64MediaExtractor.ExtractionResult mediaResult =
        Base64MediaExtractor.extract(cleanedRawQuery);
    String queryForPipeline = mediaResult.cleanedQuery();
    boolean hasMedia = mediaResult.imageBytes().isPresent();

    // Every turn goes through the pipeline, media or not (ADR-063). A branch here skipped it for a
    // prompt carrying media — and with it the orchestration switch, disable-ai, the crisis and
    // scope guards, and the entitlement check with its credit hold — for any caller who put an
    // image marker in the text. The pipeline never saw the base64 (it is lifted out above), so the
    // skip protected no interceptor; what the pipeline could not see was that an image was there,
    // and that is declared now instead of hidden.
    int mediaParts = hasMedia ? 1 : 0;
    PromptContext pipelineContext =
        Optional.ofNullable(pipeline)
            .map(p -> p.process(queryForPipeline, mediaParts, request.context()))
            .orElse(null);

    String promptText =
        Optional.ofNullable(pipelineContext)
            .map(PromptContext::refinedPrompt)
            .orElse(queryForPipeline);

    String provider = resolveProvider(pipelineContext, request.options(), defaultProvider);
    String ollamaChatModel = resolveOllamaChatModel(provider);

    // Compile message history + current prompt via MessageCompiler
    List<Message> messages = MessageCompiler.compile(request, promptText, mediaResult, hasMedia);

    // Resolve Spring AI ChatOptions via ChatOptionsResolver
    ChatOptions springOptions = request.options();
    if (ProviderClassifier.ollama().equalsIgnoreCase(provider)) {
      springOptions = ChatOptionsResolver.resolveOllamaOptions(springOptions, ollamaChatModel);
    } else if (springOptions == null) {
      springOptions = ChatOptionsResolver.resolveDefaultOptions(provider);
    }

    ChatOptions finalOptions =
        interceptors.stream()
            .reduce(
                springOptions,
                (opts, interceptor) -> interceptor.preProcess(request, promptText, messages, opts),
                (o1, o2) -> o1);

    // Assemble MCP tool callbacks via McpClientFactory
    List<AutoCloseable> closeables = new ArrayList<>();
    List<ToolCallback> allToolCallbacks =
        McpClientFactory.resolveToolCallbacks(
            request, platformMcpServerProvider, userMcpServerProvider, toolRegistry, closeables);

    if (!allToolCallbacks.isEmpty()
        && finalOptions instanceof ToolCallingChatOptions toolCallingOptions
        && !ProviderClassifier.ollama().equalsIgnoreCase(provider)) {
      // Spring AI 2.0 options are immutable: rebuild via mutate() rather than a setter.
      finalOptions = toolCallingOptions.mutate().toolCallbacks(allToolCallbacks).build();
    }

    Prompt prompt = new Prompt(messages, finalOptions);
    String conversationId = request.context().conversationId();

    return new EnginePipelineContext(
        provider, conversationId, promptText, prompt, closeables, resolveHoldId(pipelineContext));
  }

  /**
   * Lifts the credit reservation the gate recorded, so the completed turn can be settled against
   * it. The core relays an opaque string it never interprets — the interceptor that writes it and
   * the listener that settles it are the two ends that give it meaning, and both live outside.
   */
  private static String resolveHoldId(PromptContext pipelineContext) {
    return pipelineContext == null ? null : pipelineContext.billingHoldId();
  }

  /**
   * Resolves the target AI provider from pipeline routing, request options, or default.
   *
   * @param pipelineContext The result from the orchestration pipeline (nullable).
   * @param requestOptions The request-level chat options (nullable).
   * @param defaultProvider The fallback provider name.
   * @return The resolved provider string.
   * @throws CoreException If no provider can be resolved.
   */
  private String resolveProvider(
      PromptContext pipelineContext, ChatOptions requestOptions, String defaultProvider) {
    String provider =
        Optional.ofNullable(pipelineContext).map(PromptContext::routedProvider).orElse(null);

    if (provider == null || provider.isBlank()) {
      provider = ProviderClassifier.resolveFromOptions(requestOptions);
    }
    if (provider == null || provider.isBlank()) {
      provider = defaultProvider;
    }
    if (provider == null || provider.isBlank()) {
      throw new CoreException("Missing required property: orazaka.core.default-provider");
    }
    return provider;
  }

  /**
   * Resolves the active Ollama chat model name when provider is Ollama.
   *
   * @param provider The resolved provider name.
   * @return The active Ollama model name, or null if provider is not Ollama.
   * @throws CoreException If provider is Ollama but no active model is registered.
   */
  private String resolveOllamaChatModel(String provider) {
    if (!ProviderClassifier.ollama().equalsIgnoreCase(provider)) {
      return null;
    }
    return modelCatalogProvider
        .getActiveChatModel()
        .orElseThrow(
            () ->
                new CoreException(
                    "Capability missing: No active Ollama chat model found in the model registry."));
  }

  /**
   * Strips tool/function-calling definitions from a {@link Prompt}, preserving only the core model,
   * temperature, and token limit settings.
   *
   * <p>This is used as a fallback when tool execution activation fails with {@link
   * IllegalStateException}, allowing inference to proceed without tools.
   *
   * @param prompt The original prompt potentially containing tool definitions.
   * @return A new prompt with clean options (no tool definitions).
   */
  public static Prompt removeTools(Prompt prompt) {
    ChatOptions options = prompt.getOptions();
    if (options == null) {
      return prompt;
    }
    ChatOptions cleanOptions =
        switch (options) {
          case OllamaChatOptions ollama ->
              OllamaChatOptions.builder()
                  .model(ollama.getModel())
                  .temperature(ollama.getTemperature())
                  .numPredict(ollama.getNumPredict())
                  .numCtx(8192)
                  .build();
          case OpenAiChatOptions openai ->
              OpenAiChatOptions.builder()
                  .model(openai.getModel())
                  .temperature(openai.getTemperature())
                  .maxTokens(openai.getMaxTokens())
                  .build();
          default -> options;
        };
    return new Prompt(prompt.getInstructions(), cleanOptions);
  }
}
