package com.orazaka.persistence.infrastructure.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orazaka.persistence.domain.model.PendingOutboxEvent;
import com.orazaka.persistence.domain.ports.inbound.OutboxStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

  @Mock private OutboxStore outboxStore;
  @Mock private RabbitTemplate rabbitTemplate;

  private OutboxRelay relay;

  @BeforeEach
  void setUp() {
    relay = new OutboxRelay(outboxStore, rabbitTemplate, new ObjectMapper());
  }

  private static PendingOutboxEvent event(int attempts) {
    return new PendingOutboxEvent(
        UUID.randomUUID(),
        "orazaka.jobs",
        "job.text.process",
        UUID.randomUUID(),
        Map.of("jobId", "job-1"),
        attempts);
  }

  @Test
  @DisplayName("Publishes each locked event with the row's messageId and marks it published")
  void publishesAndMarks() {
    PendingOutboxEvent pending = event(0);
    when(outboxStore.lockPendingBatch(100)).thenReturn(List.of(pending));

    relay.relayPendingBatch();

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(rabbitTemplate).send(eq("orazaka.jobs"), eq("job.text.process"), captor.capture());
    Message sent = captor.getValue();
    assertThat(sent.getMessageProperties().getMessageId())
        .isEqualTo(pending.messageId().toString());
    assertThat(sent.getMessageProperties().getContentType()).isEqualTo("application/json");
    assertThat(new String(sent.getBody())).contains("\"jobId\":\"job-1\"");
    verify(outboxStore).markPublished(pending.id());
    verify(outboxStore, never()).recordFailure(any(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  @DisplayName("A failed publish records the failure and does not mark the event published")
  void failedPublishRecordsFailure() {
    PendingOutboxEvent pending = event(2);
    when(outboxStore.lockPendingBatch(100)).thenReturn(List.of(pending));
    doThrow(new AmqpException("broker down"))
        .when(rabbitTemplate)
        .send(eq("orazaka.jobs"), eq("job.text.process"), any(Message.class));

    relay.relayPendingBatch();

    verify(outboxStore).recordFailure(pending.id(), 2);
    verify(outboxStore, never()).markPublished(pending.id());
  }

  @Test
  @DisplayName("One poisoned event does not block the rest of the batch")
  void poisonedEventDoesNotBlockBatch() {
    PendingOutboxEvent failing = event(0);
    PendingOutboxEvent healthy = event(0);
    when(outboxStore.lockPendingBatch(100)).thenReturn(List.of(failing, healthy));
    doThrow(new AmqpException("boom"))
        .doNothing()
        .when(rabbitTemplate)
        .send(eq("orazaka.jobs"), eq("job.text.process"), any(Message.class));

    relay.relayPendingBatch();

    verify(outboxStore).recordFailure(failing.id(), 0);
    verify(outboxStore).markPublished(healthy.id());
  }

  @Test
  @DisplayName("Purge delegates the retention cutoff to the store")
  void purgeDelegates() {
    when(outboxStore.purgePublishedBefore(any(Instant.class))).thenReturn(2L);

    relay.purgePublished();

    verify(outboxStore).purgePublishedBefore(any(Instant.class));
  }
}
