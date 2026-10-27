package com.krizaka.orazaka.core.application;

import com.krizaka.orazaka.core.domain.model.chat.PreparedPrompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/** Test double of the adapter's prompt: the core's tests cannot see the adapter module. */
public record SpringPreparedPrompt(Prompt prompt) implements PreparedPrompt {

  @Override
  public int messageCount() {
    return prompt.getInstructions().size();
  }

  @Override
  public String modelName() {
    return prompt.getOptions() == null ? null : prompt.getOptions().getModel();
  }

  @Override
  public PreparedPrompt withoutTools() {
    return new SpringPreparedPrompt(
        new Prompt(
            prompt.getInstructions(),
            prompt.getOptions() == null
                ? null
                : ChatOptions.builder()
                    .model(prompt.getOptions().getModel())
                    .temperature(prompt.getOptions().getTemperature())
                    .maxTokens(prompt.getOptions().getMaxTokens())
                    .build()));
  }
}
