package com.orazaka.core.infrastructure.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/** Dynamic ChatModel provider for OpenAI models (Spring AI 2.0 / official com.openai SDK). */
@Component
public class OpenAiDynamicProvider implements DynamicChatModelProvider {

  @Override
  public boolean supports(String providerName) {
    return "openai".equalsIgnoreCase(providerName);
  }

  @Override
  public ChatModel create(String modelName, String apiKey) {
    if (modelName == null || modelName.isBlank()) {
      throw new IllegalArgumentException("Model name is required for OpenAI dynamic provider");
    }
    // Spring AI 2.0: OpenAiChatModel.build() derives its (sync + async) com.openai client from the
    // options via OpenAiSetup, so the credential must live on the options, not a hand-built client.
    return OpenAiChatModel.builder()
        .options(OpenAiChatOptions.builder().model(modelName).apiKey(apiKey).build())
        .build();
  }
}
