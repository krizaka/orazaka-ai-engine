package com.orazaka.persistence.infrastructure.adapter.messaging;

import com.orazaka.persistence.domain.model.PendingOutboxEvent;
import com.orazaka.persistence.domain.ports.inbound.OutboxStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Drains the transactional outbox to RabbitMQ (AGENTS.md §6): locks a batch of unpublished rows,
 * publishes each with the row's {@code messageId} as the AMQP message id (consumer-side dedup key),
 * and marks it published in the same transaction. A failed publish records the attempt and backs
 * off exponentially — a broker outage defers delivery instead of failing the producer.
 */
@Component
class OutboxRelay {

  private static final Logger logger = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int BATCH_SIZE = 100;
  private static final long PUBLISHED_RETENTION_DAYS = 7;

  private final OutboxStore outboxStore;
  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;

  OutboxRelay(OutboxStore outboxStore, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
    this.outboxStore = Objects.requireNonNull(outboxStore, "OutboxStore cannot be null");
    this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "RabbitTemplate cannot be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
  }

  /**
   * Publishes the next batch of pending events. Runs on a virtual thread ({@code
   * spring.threads.virtual.enabled}); the transaction spans lock → publish → mark so an interrupted
   * batch is simply retried (at-least-once + messageId dedup).
   */
  @Scheduled(fixedDelayString = "${orazaka.messaging.outbox.poll-interval-ms:500}")
  @Transactional
  public void relayPendingBatch() {
    for (PendingOutboxEvent event : outboxStore.lockPendingBatch(BATCH_SIZE)) {
      try {
        rabbitTemplate.send(event.exchange(), event.routingKey(), toAmqpMessage(event));
        outboxStore.markPublished(event.id());
      } catch (RuntimeException e) {
        logger.warn(
            "Outbox publish failed for event {} ({} -> {}), attempt {} — backing off",
            event.id(),
            event.exchange(),
            event.routingKey(),
            event.attempts() + 1,
            e);
        outboxStore.recordFailure(event.id(), event.attempts());
      }
    }
  }

  /** Hourly housekeeping: drops published rows older than the retention window. */
  @Scheduled(fixedDelayString = "${orazaka.messaging.outbox.purge-interval-ms:3600000}")
  public void purgePublished() {
    long purged =
        outboxStore.purgePublishedBefore(
            Instant.now().minus(PUBLISHED_RETENTION_DAYS, ChronoUnit.DAYS));
    if (purged > 0) {
      logger.info("Purged {} published outbox events", purged);
    }
  }

  private Message toAmqpMessage(PendingOutboxEvent event) {
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setMessageId(event.messageId().toString());
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    // spring-amqp 4.0.0 (Boot 4) SimpleAmqpHeaderMapper.toHeaders NPEs on a null AMQP
    // priority; set an explicit 0 so @Header-mapping consumers never hit it.
    properties.setPriority(0);
    return new Message(objectMapper.writeValueAsBytes(event.payload()), properties);
  }
}
