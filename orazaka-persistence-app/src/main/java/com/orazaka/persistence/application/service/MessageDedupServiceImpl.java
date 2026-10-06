package com.orazaka.persistence.application.service;

import com.orazaka.persistence.domain.ports.inbound.MessageDedupService;
import com.orazaka.persistence.infrastructure.adapter.persistence.entity.ProcessedMessageEntity;
import com.orazaka.persistence.infrastructure.adapter.persistence.entity.ProcessedMessageKey;
import com.orazaka.persistence.infrastructure.adapter.persistence.repository.ProcessedMessageRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Package-private implementation of the consumer-side {@link MessageDedupService}. */
@Service
class MessageDedupServiceImpl implements MessageDedupService {

  private static final Logger logger = LoggerFactory.getLogger(MessageDedupServiceImpl.class);
  private static final long RETENTION_DAYS = 7;

  private final ProcessedMessageRepository repository;

  MessageDedupServiceImpl(ProcessedMessageRepository repository) {
    this.repository =
        Objects.requireNonNull(repository, "ProcessedMessageRepository cannot be null");
  }

  /**
   * {@inheritDoc}
   *
   * <p>The unique constraint on {@code (consumer, message_id)} is the arbiter: two concurrent
   * deliveries both attempt the insert and exactly one succeeds. This class used to implement the
   * two-step {@code isDuplicate}/{@code markProcessed}, which is a read followed by a write and
   * loses the race it exists to win (ADR-058 §2).
   */
  @Override
  public boolean claim(String consumer, String messageId) {
    if (messageId == null || messageId.isBlank()) {
      return true;
    }
    try {
      // saveAndFlush, not save: the constraint has to be hit HERE, not at transaction commit,
      // or the caller is told it may process and finds out otherwise much later.
      repository.saveAndFlush(
          new ProcessedMessageEntity(
              new ProcessedMessageKey(consumer, messageId), java.time.Instant.now()));
      return true;
    } catch (DataIntegrityViolationException alreadyClaimed) {
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void release(String consumer, String messageId) {
    if (messageId == null || messageId.isBlank()) {
      return;
    }
    repository.deleteById(new ProcessedMessageKey(consumer, messageId));
  }
}
