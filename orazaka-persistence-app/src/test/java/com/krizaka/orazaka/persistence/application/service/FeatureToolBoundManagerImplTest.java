package com.krizaka.orazaka.persistence.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.entity.FeatureToolBoundEntity;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.repository.FeatureToolBoundRepository;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureToolBoundManagerImplTest {

  @Mock private FeatureToolBoundRepository repository;

  @InjectMocks private FeatureToolBoundManagerImpl manager;

  private static FeatureToolBoundEntity row(String feature, int rounds) {
    FeatureToolBoundEntity entity = new FeatureToolBoundEntity();
    entity.setFeatureKey(feature);
    entity.setMaxToolRounds(rounds);
    return entity;
  }

  @Test
  @DisplayName("a feature's own row wins over the default row")
  void ownRowWins() {
    when(repository.findById("agent.code.issue-to-pr"))
        .thenReturn(Optional.of(row("agent.code.issue-to-pr", 3)));

    assertEquals(OptionalInt.of(3), manager.maxToolRounds("agent.code.issue-to-pr"));
    verifyNoMoreInteractions(repository);
  }

  @Test
  @DisplayName("a feature with no row reads the default row")
  void defaultRow() {
    when(repository.findById("agent.other")).thenReturn(Optional.empty());
    when(repository.findById("*")).thenReturn(Optional.of(row("*", 8)));

    assertEquals(OptionalInt.of(8), manager.maxToolRounds("agent.other"));
  }

  @Test
  @DisplayName("a null or blank key reads the default row")
  void blankKeyReadsDefault() {
    when(repository.findById("*")).thenReturn(Optional.of(row("*", 8)));

    assertEquals(OptionalInt.of(8), manager.maxToolRounds(null));
    assertEquals(OptionalInt.of(8), manager.maxToolRounds(" "));
  }

  @Test
  @DisplayName("no own row and no default row is empty, the caller keeps its safe default")
  void nothingIsEmpty() {
    when(repository.findById("agent.other")).thenReturn(Optional.empty());
    when(repository.findById("*")).thenReturn(Optional.empty());

    assertTrue(manager.maxToolRounds("agent.other").isEmpty());
  }
}
