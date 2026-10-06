package com.orazaka.interceptor.reformulation;

import java.util.Optional;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

/**
 * Utility class with shared helper methods for the reformulation interceptor module.
 *
 * <p>Provides the {@link #extractResponseText} utility used by both {@link RefinerInterceptor} and
 * {@link RouterInterceptor} to safely parse ChatModel responses.
 *
 * @since 1.0.0
 */
public final class ReformulationUtils {

  /** Private constructor — utility class, not instantiable. */
  private ReformulationUtils() {}

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
