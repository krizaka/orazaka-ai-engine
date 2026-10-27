package com.krizaka.orazaka.adapter.springai.structured;

import com.krizaka.orazaka.core.domain.model.structured.StructuredOutputRequest;
import com.krizaka.orazaka.core.domain.ports.outbound.StructuredOutputClient;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * Spring AI implementation of {@link StructuredOutputClient}: the answer's format is appended to
 * the instruction and the reply is converted to the requested type.
 */
public class SpringAiStructuredOutputClient implements StructuredOutputClient {

  private final ChatModel chatModel;

  /**
   * Creates the client.
   *
   * @param chatModel the model asked
   */
  public SpringAiStructuredOutputClient(ChatModel chatModel) {
    this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
  }

  @Override
  public <T> T generate(StructuredOutputRequest<T> request) {
    var converter = new BeanOutputConverter<>(request.type());
    String system =
        request.systemPrompt().isBlank()
            ? converter.getFormat()
            : request.systemPrompt() + "\n\n" + converter.getFormat();
    var response =
        chatModel.call(
            new Prompt(List.of(new SystemMessage(system), new UserMessage(request.userPrompt()))));
    if (response == null || response.getResult() == null) {
      throw new IllegalStateException("The model returned no answer.");
    }
    try {
      T value = converter.convert(response.getResult().getOutput().getText());
      return Objects.requireNonNull(value, "The model's answer was empty.");
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "The model's answer is not a " + request.type().getSimpleName(), e);
    }
  }
}
