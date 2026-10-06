package com.orazaka.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * Composite primary key of {@link ProcessedMessageEntity}: one dedup row per (consumer, messageId).
 */
@Embeddable
public record ProcessedMessageKey(String consumer, String messageId) {

  public ProcessedMessageKey {
    Objects.requireNonNull(consumer, "consumer cannot be null");
    Objects.requireNonNull(messageId, "messageId cannot be null");
  }
}
