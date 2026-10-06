package com.orazaka.persistence.infrastructure.adapter.persistence.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

/**
 * JPA entity recording a fully processed AMQP message (consumer-side dedup, AGENTS.md §6).
 *
 * <p>Implements {@link Persistable} reporting {@code isNew() == true} so {@code save(...)} always
 * INSERTs (insert-first, ERR-109): a concurrent duplicate collapses on the primary key as a {@code
 * DataIntegrityViolationException} instead of silently merging.
 */
@Entity
@Table(name = "processed_messages")
public class ProcessedMessageEntity implements Persistable<ProcessedMessageKey> {

  @EmbeddedId
  @AttributeOverride(name = "consumer", column = @Column(name = "consumer", nullable = false))
  @AttributeOverride(name = "messageId", column = @Column(name = "message_id", nullable = false))
  private ProcessedMessageKey key;

  @Column(name = "processed_at", nullable = false, updatable = false)
  private Instant processedAt;

  /** JPA-required no-arg constructor. */
  protected ProcessedMessageEntity() {}

  public ProcessedMessageEntity(ProcessedMessageKey key, Instant processedAt) {
    this.key = key;
    this.processedAt = processedAt;
  }

  @Override
  public ProcessedMessageKey getId() {
    return key;
  }

  @Override
  public boolean isNew() {
    // Dedup rows are only ever inserted, never loaded for update.
    return true;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
