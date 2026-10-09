package com.krizaka.orazaka.core.infrastructure.config;

import com.krizaka.orazaka.core.application.pipeline.PipelineOptionsStrategy;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/** OpenAI-specific options strategy implementation. */
@Component
public class OpenAiOptionsStrategy implements PipelineOptionsStrategy {

  @Override
  public boolean supports(String provider) {
    return "openai".equalsIgnoreCase(provider);
  }

  @Override
  public ChatOptions buildOptions(String modelName, Double temperature) {
    return OpenAiChatOptions.builder().model(modelName).temperature(temperature).build();
  }
}
