package com.krizaka.orazaka.interceptor.reformulation;

import com.krizaka.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.orazaka.core.application.pipeline.PipelineOptionsRegistry;
import com.krizaka.orazaka.core.domain.model.PromptContext;
import com.krizaka.orazaka.core.infrastructure.config.CoreProperties;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

/**
 * Order 6 — Resolves fuzzy user questions against compiled context matrices and reformulates them
 * into clear, explicit instructions using an LLM call.
 *
 * <p>This interceptor is <strong>AI-dependent</strong> — it invokes an LLM model to refine the
 * prompt. The security governance kill-switch will block this interceptor when active.
 *
 * @since 1.0.0
 */
public class RefinerInterceptor implements PromptContextInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(RefinerInterceptor.class);

  private final Map<String, ChatModel> chatModels;
  private final CoreProperties properties;
  private final PipelineOptionsRegistry optionsRegistry;
  private final Resource systemRefinementResource;
  private final Resource contextEnvelopeResource;

  public RefinerInterceptor(
      Map<String, ChatModel> chatModels,
      CoreProperties properties,
      PipelineOptionsRegistry optionsRegistry,
      @Value("classpath:/prompts/system-refinement.st") Resource systemRefinementResource,
      @Value("classpath:/prompts/context-envelope.st") Resource contextEnvelopeResource) {
    this.chatModels = Map.copyOf(Objects.requireNonNullElse(chatModels, Map.of()));
    this.properties = Objects.requireNonNull(properties, "CoreProperties must not be null");
    this.optionsRegistry =
        Objects.requireNonNull(optionsRegistry, "PipelineOptionsRegistry must not be null");
    this.systemRefinementResource =
        Objects.requireNonNull(
            systemRefinementResource, "systemRefinementResource must not be null");
    this.contextEnvelopeResource =
        Objects.requireNonNull(contextEnvelopeResource, "contextEnvelopeResource must not be null");
  }

  @Override
  public boolean isAiDependent() {
    return true;
  }

  @Override
  public PromptContext intercept(PromptContext context) {
    if (!isRefinerConfigured()) return context;
    if (context.carriesMedia()) {
      // The refinement envelope carries the text and the metadata — never the image. Refining "what
      // do you think?" without the photograph rewrites a question about content the refiner never
      // saw, and the rewrite, not the user's words, is what the vision model then receives beside
      // the image. Until ADR-063 media turns never reached this stage; they do now, and the user's
      // words are what they send, as before.
      logger.debug("Turn carries media the refiner cannot see; keeping the user's words.");
      return context;
    }

    String provider = resolveProvider();
    ChatModel model = chatModels.get(provider);
    if (model == null) {
      model =
          chatModels.entrySet().stream()
              .filter(entry -> entry.getKey().toLowerCase().contains(provider.toLowerCase()))
              .map(Map.Entry::getValue)
              .findFirst()
              .orElse(null);
    }
    if (model == null) {
      logger.warn("Refinement provider '{}' is not registered.", provider);
      return context;
    }

    Prompt prompt = buildRefinementPrompt(provider, context);
    return executeRefinement(model, prompt, context);
  }

  /**
   * Guards against missing refiner model wiring. Per-interceptor enable/disable is enforced
   * upstream by the {@code DynamicPipelineExecutor} from {@code pipeline_interceptor_config} — this
   * interceptor only runs when the executor has already deemed it enabled.
   */
  private boolean isRefinerConfigured() {
    return properties.orchestration() != null && properties.orchestration().refiner() != null;
  }

  private String resolveProvider() {
    return Optional.ofNullable(properties.orchestration())
        .map(CoreProperties.OrchestrationConfig::refiner)
        .map(CoreProperties.InterceptorConfig::provider)
        .orElse(properties.defaultProvider());
  }

  private Prompt buildRefinementPrompt(String provider, PromptContext context) {
    var sysMsg = new SystemPromptTemplate(systemRefinementResource).createMessage();
    var userMsg =
        new PromptTemplate(contextEnvelopeResource)
            .createMessage(
                Map.of(
                    "userMetadata", context.userMetadata().toString(),
                    "systemMetadata", context.systemMetadata().toString(),
                    "rawQuery", context.rawUserQuery()));

    var opt = properties.orchestration().refiner();
    ChatOptions options = optionsRegistry.build(provider, opt.model(), opt.temperature());
    return new Prompt(List.of(sysMsg, userMsg), options);
  }

  private PromptContext executeRefinement(ChatModel model, Prompt prompt, PromptContext context) {
    try {
      return ReformulationUtils.extractResponseText(model.call(prompt))
          .filter(refined -> !refined.isBlank())
          .map(
              refined -> {
                logger.debug("Refinement generated ({} chars)", refined.length());
                return context.withRefinedPrompt(refined);
              })
          .orElse(context);
    } catch (RuntimeException e) {
      logger.error("Failed to refine prompt.", e);
      return context;
    }
  }
}
