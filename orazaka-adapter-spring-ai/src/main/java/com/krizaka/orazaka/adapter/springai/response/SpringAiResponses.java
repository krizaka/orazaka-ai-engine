package com.krizaka.orazaka.adapter.springai.response;

import java.util.Optional;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * Reads the text of a Spring AI response, so no interceptor handles the response types itself.
 *
 * <p>Provides the {@link #extractResponseText} utility used by both {@link RefinerInterceptor} and
 * {@link RouterInterceptor} to safely parse ChatModel responses.
 *
 * @since 1.0.0
 */
public final class SpringAiResponses {

  /** Private constructor — utility class, not instantiable. */
  private SpringAiResponses() {}

  /**
   * Safely extracts the trimmed response text from a {@link ChatResponse}.
   *
   * @param response The ChatResponse from a model call (nullable).
   * @return An {@link Optional} containing the non-blank, stripped text, or empty.
   */
  public static Optional<String> extractResponseText(ChatResponse response) {
    return Optional.ofNullable(response)
        .map(ChatResponse::getResult)
        .map(Generation::getOutput)
        .map(AssistantMessage::getText)
        .map(String::strip)
        .filter(text -> !text.isBlank());
  }
}
