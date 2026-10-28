package com.krizaka.orazaka.adapter.springai.prompt;

import com.krizaka.orazaka.core.domain.model.chat.PreparedPrompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * The prompt the assembly builds, in Spring AI's type: the engines hold a {@link PreparedPrompt},
 * only the chat invocation adapter opens this.
 *
 * @param prompt the assembled prompt
 */
public record SpringPreparedPrompt(Prompt prompt) implements PreparedPrompt {

  @Override
  public int messageCount() {
    return prompt.getInstructions().size();
  }

  @Override
  public String modelName() {
    return SpringAiChatOptionsInfo.modelNameOf(prompt.getOptions());
  }

  @Override
  public PreparedPrompt withoutTools() {
    return new SpringPreparedPrompt(removeTools(prompt));
  }

  /**
   * Strips tool definitions from a prompt, keeping the model, temperature and token limit.
   *
   * @param prompt the prompt
   * @return the prompt with clean options
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
