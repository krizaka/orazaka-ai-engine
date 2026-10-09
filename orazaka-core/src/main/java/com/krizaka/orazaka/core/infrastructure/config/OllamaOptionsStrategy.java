package com.krizaka.orazaka.core.infrastructure.config;

import com.krizaka.orazaka.core.application.pipeline.PipelineOptionsStrategy;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Component;

/** Ollama-specific options strategy implementation. */
@Component
public class OllamaOptionsStrategy implements PipelineOptionsStrategy {

  @Override
  public boolean supports(String provider) {
    return "ollama".equalsIgnoreCase(provider);
  }

  @Override
  public ChatOptions buildOptions(String modelName, Double temperature) {
    return OllamaChatOptions.builder()
        .model(modelName)
        .temperature(temperature)
        .numCtx(8192)
        .build();
  }
}
