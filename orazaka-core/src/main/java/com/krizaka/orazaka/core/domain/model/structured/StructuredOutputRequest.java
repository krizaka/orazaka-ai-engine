package com.krizaka.orazaka.core.domain.model.structured;

import java.util.Objects;

/**
 * Asks a model for an answer that is an instance of {@code type} (pure Java: no framework type).
 *
 * @param systemPrompt the instruction, may be empty
 * @param userPrompt the question
 * @param type the shape the answer must have
 * @param <T> the shape
 */
public record StructuredOutputRequest<T>(String systemPrompt, String userPrompt, Class<T> type) {

  /** Validates the required fields. */
  public StructuredOutputRequest {
    systemPrompt = systemPrompt == null ? "" : systemPrompt;
    Objects.requireNonNull(userPrompt, "userPrompt");
    Objects.requireNonNull(type, "type");
  }
}
