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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

/**
 * Order 7 — Analyzes user intent and selects the optimal AI provider using an LLM classification
 * call with temperature 0.0 for determinism.
 *
 * <p>This interceptor is <strong>AI-dependent</strong> — it invokes an LLM model to determine
 * routing. The security governance kill-switch will block this interceptor when active.
 *
 * @since 1.0.0
 */
public class RouterInterceptor implements PromptContextInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(RouterInterceptor.class);

  private final Map<String, ChatModel> chatModels;
  private final CoreProperties properties;
  private final PipelineOptionsRegistry optionsRegistry;
  private final Resource routerSystemResource;

  public RouterInterceptor(
      Map<String, ChatModel> chatModels,
      CoreProperties properties,
      PipelineOptionsRegistry optionsRegistry,
      @Value("classpath:/prompts/system-router.st") Resource routerSystemResource) {
    this.chatModels = Map.copyOf(Objects.requireNonNullElse(chatModels, Map.of()));
    this.properties = Objects.requireNonNull(properties, "CoreProperties must not be null");
    this.optionsRegistry =
        Objects.requireNonNull(optionsRegistry, "PipelineOptionsRegistry must not be null");
    this.routerSystemResource =
        Objects.requireNonNull(routerSystemResource, "routerSystemResource must not be null");
  }

  @Override
  public boolean isAiDependent() {
    return true;
  }

  @Override
  public PromptContext intercept(PromptContext context) {
    if (!isRouterConfigured()) return context;
    if (context.carriesMedia()) {
      // The router chooses from the text alone and cannot tell which provider can see an image —
      // and a routed provider outranks the caller's own. A vision job that names its vision model
      // would be sent wherever "Analyze this image" reads best. Unrouted, the turn keeps the
      // provider its caller chose, as it did while the pipeline skipped media turns (ADR-063).
      logger.debug("Turn carries media the router cannot see; leaving the provider to the caller.");
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
      logger.warn("Router provider '{}' is not registered.", provider);
      return context;
    }

    Prompt prompt = buildRouterPrompt(provider, context);
    return executeRouting(model, prompt, context);
  }

  /**
   * Guards against missing router model wiring. Per-interceptor enable/disable is enforced upstream
   * by the {@code DynamicPipelineExecutor} from {@code pipeline_interceptor_config} — this
   * interceptor only runs when the executor has already deemed it enabled.
   */
  private boolean isRouterConfigured() {
    return properties.orchestration() != null && properties.orchestration().router() != null;
  }

  private String resolveProvider() {
    return Optional.ofNullable(properties.orchestration())
        .map(CoreProperties.OrchestrationConfig::router)
        .map(CoreProperties.InterceptorConfig::provider)
        .orElse(properties.defaultProvider());
  }

  private Prompt buildRouterPrompt(String provider, PromptContext context) {
    String availableModels = String.join(", ", chatModels.keySet().stream().sorted().toList());
    var sysMsg = new SystemPromptTemplate(routerSystemResource).createMessage();
    // User message is inlined (no router-user.st core resource — the core prompt
    // allow-list only permits system-router.st / system-refinement.st / context-envelope.st).
    var userMsg =
        new UserMessage(
            "Refined query:\n"
                + context.refinedPrompt()
                + "\n\nLocally available model keys: "
                + availableModels
                + "\n\nReturn only the optimal provider key.");

    var opt = properties.orchestration().router();
    ChatOptions options = optionsRegistry.build(provider, opt.model(), 0.0);
    return new Prompt(List.of(sysMsg, userMsg), options);
  }

  private PromptContext executeRouting(ChatModel model, Prompt prompt, PromptContext context) {
    try {
      return ReformulationUtils.extractResponseText(model.call(prompt))
          .filter(chatModels::containsKey)
          .map(
              routedModel -> {
                logger.debug("Routed to: {}", routedModel);
                return context.withRoutedProvider(routedModel);
              })
          .orElse(context);
    } catch (RuntimeException e) {
      logger.error("Failed to route prompt.", e);
      return context;
    }
  }
}
