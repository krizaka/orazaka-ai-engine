package com.krizaka.orazaka.core.infrastructure.provider;

import com.google.genai.Client;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.stereotype.Component;

/** Dynamic ChatModel provider for Google Gemini models. */
@Component
public class GeminiDynamicProvider implements DynamicChatModelProvider {

  @Override
  public boolean supports(String providerName) {
    return "gemini".equalsIgnoreCase(providerName) || "google".equalsIgnoreCase(providerName);
  }

  @Override
  public ChatModel create(String modelName, String apiKey) {
    if (modelName == null || modelName.isBlank()) {
      throw new IllegalArgumentException("Model name is required for Gemini dynamic provider");
    }
    Client client = Client.builder().apiKey(apiKey).build();
    GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().model(modelName).build();
    return GoogleGenAiChatModel.builder().genAiClient(client).options(options).build();
  }
}
