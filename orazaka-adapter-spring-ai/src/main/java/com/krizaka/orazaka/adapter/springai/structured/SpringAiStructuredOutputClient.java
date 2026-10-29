package com.krizaka.orazaka.adapter.springai.structured;

import com.krizaka.orazaka.core.domain.model.structured.StructuredOutputInvalidException;
import com.krizaka.orazaka.core.domain.model.structured.StructuredOutputRequest;
import com.krizaka.orazaka.core.domain.ports.outbound.StructuredOutputBounds;
import com.krizaka.orazaka.core.domain.ports.outbound.StructuredOutputClient;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Spring AI implementation of {@link StructuredOutputClient} (ADR-094 §4.6, #172): a {@link
 * ChatClient} appends the format of the requested type, Spring AI's {@link
 * StructuredOutputValidationAdvisor} validates each answer against the type's JSON schema and
 * re-asks the model with the violation, and the attempts are bounded by {@link
 * StructuredOutputBounds}. Out of attempts, the call fails with a {@link
 * StructuredOutputInvalidException}: never a default value.
 */
public class SpringAiStructuredOutputClient implements StructuredOutputClient {

  private final ChatModel chatModel;
  private final StructuredOutputBounds bounds;
  private final ObservationRegistry observations;

  /**
   * Creates the client.
   *
   * @param chatModel the model asked
   * @param bounds how many answers one call may ask for
   * @param observations where the chat client's observations go
   */
  public SpringAiStructuredOutputClient(
      ChatModel chatModel, StructuredOutputBounds bounds, ObservationRegistry observations) {
    this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
    this.bounds = Objects.requireNonNull(bounds, "bounds");
    this.observations = Objects.requireNonNull(observations, "observations");
  }

  @Override
  public <T> T generate(StructuredOutputRequest<T> request) {
    int attempts = Math.max(1, bounds.maxAttempts());
    var validation =
        StructuredOutputValidationAdvisor.builder()
            .outputType(request.type())
            .maxRepeatAttempts(attempts - 1)
            .build();
    var spec =
        ChatClient.builder(chatModel, observations, null, null)
            .defaultAdvisors(validation)
            .build()
            .prompt();
    if (!request.systemPrompt().isBlank()) {
      spec = spec.system(request.systemPrompt());
    }
    try {
      T value = spec.user(request.userPrompt()).call().entity(request.type());
      return Objects.requireNonNull(value, "The model's answer was empty.");
    } catch (RuntimeException e) {
      throw new StructuredOutputInvalidException(
          request.type().getSimpleName(), attempts, String.valueOf(e.getMessage()), e);
    }
  }
}
