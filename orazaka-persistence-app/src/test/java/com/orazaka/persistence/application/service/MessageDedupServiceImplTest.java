package com.orazaka.persistence.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orazaka.persistence.infrastructure.adapter.persistence.entity.ProcessedMessageEntity;
import com.orazaka.persistence.infrastructure.adapter.persistence.entity.ProcessedMessageKey;
import com.orazaka.persistence.infrastructure.adapter.persistence.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Both halves of the invariant: seen twice, processed once — and failed, seen again (ADR-058). */
class MessageDedupServiceImplTest {

  private final ProcessedMessageRepository repository = mock(ProcessedMessageRepository.class);
  private final MessageDedupServiceImpl service = new MessageDedupServiceImpl(repository);

  @Test
  @DisplayName("the first caller claims it")
  void firstCallerClaims() {
    when(repository.saveAndFlush(any(ProcessedMessageEntity.class))).thenReturn(null);

    assertThat(service.claim("consumer", "m-1")).isTrue();
  }

  @Test
  @DisplayName("the second is refused by the constraint, not by a prior read")
  void secondCallerIsRefused() {
    // The whole point: the decision is the database's, in one statement. A SELECT followed by an
    // INSERT is two statements a concurrent delivery slips between — which is what three of the
    // four implementations of this invariant used to do.
    when(repository.saveAndFlush(any(ProcessedMessageEntity.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key"));

    assertThat(service.claim("consumer", "m-1")).isFalse();
  }

  @Test
  @DisplayName("a released claim can be taken again — a failed message is not lost")
  void releaseMakesItClaimableAgain() {
    service.release("consumer", "m-1");

    verify(repository).deleteById(new ProcessedMessageKey("consumer", "m-1"));
  }

  @Test
  @DisplayName("no messageId means no deduplication was asked for, so it is claimable")
  void aMissingIdIsClaimable() {
    assertThat(service.claim("consumer", null)).isTrue();
    assertThat(service.claim("consumer", "  ")).isTrue();
    verify(repository, never()).saveAndFlush(any(ProcessedMessageEntity.class));
  }

  @Test
  @DisplayName("releasing nothing touches nothing")
  void releasingABlankIdIsANoOp() {
    service.release("consumer", null);

    verify(repository, never()).deleteById(any());
  }
}
