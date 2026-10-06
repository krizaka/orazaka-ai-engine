package com.orazaka.core.application.routing;

import com.orazaka.core.infrastructure.provider.DynamicChatModelProvider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * Factory that dynamically instantiates Spring AI ChatModel beans in memory on a request-scoped
 * basis using the dynamic strategy pattern.
 */
@Component
public class DynamicChatModelFactory {

  private static final Logger logger = LoggerFactory.getLogger(DynamicChatModelFactory.class);
  private final List<DynamicChatModelProvider> providers;

  /**
   * Constructs the factory, auto-injecting all implementations of {@link DynamicChatModelProvider}.
   *
   * @param providers List of dynamic chat model providers.
   */
  public DynamicChatModelFactory(List<DynamicChatModelProvider> providers) {
    this.providers = providers;
  }

  /**
   * Instantiates a dynamic ChatModel for the given provider, model, and decrypted API key.
   *
   * @param providerName The provider name ("openai", "claude"/"anthropic", "gemini").
   * @param modelName The target model name override.
   * @param apiKey The user's decrypted API key.
   * @return A dynamic request-scoped ChatModel.
   */
  public ChatModel createChatModel(String providerName, String modelName, String apiKey) {
    if (modelName == null || modelName.isBlank()) {
      throw new IllegalArgumentException("Model name must be explicitly provided");
    }

    logger.info(
        "[ORAZAKA-SIM-MESH] Intent split detected: instantiating dynamic model for provider={}, model={}",
        providerName,
        modelName);

    return providers.stream()
        .filter(p -> p.supports(providerName))
        .findFirst()
        .map(p -> p.create(modelName, apiKey))
        .orElseThrow(
            () -> new IllegalArgumentException("Unsupported dynamic provider: " + providerName));
  }
}
