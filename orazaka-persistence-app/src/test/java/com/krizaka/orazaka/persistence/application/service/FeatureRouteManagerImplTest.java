package com.krizaka.orazaka.persistence.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.entity.FeatureModelEntity;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.repository.FeatureModelRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureRouteManagerImplTest {

  @Mock private FeatureModelRepository repository;

  @InjectMocks private FeatureRouteManagerImpl manager;

  private static FeatureModelEntity row(String feature, int rank, String model) {
    FeatureModelEntity entity = new FeatureModelEntity();
    entity.setFeatureKey(feature);
    entity.setRank(rank);
    entity.setModelName(model);
    return entity;
  }

  @Test
  @DisplayName("a feature's models come back in the order the repository ranks them")
  void modelsInRankOrder() {
    when(repository.findByFeatureKeyOrderByRankAsc("agent.code.issue-to-pr"))
        .thenReturn(
            List.of(
                row("agent.code.issue-to-pr", 1, "qwen2.5-coder:7b"),
                row("agent.code.issue-to-pr", 2, "other-model")));

    assertEquals(
        List.of("qwen2.5-coder:7b", "other-model"), manager.modelNames("agent.code.issue-to-pr"));
    verify(repository).findByFeatureKeyOrderByRankAsc("agent.code.issue-to-pr");
  }

  @Test
  @DisplayName("a feature with no row has no route, which is not an error")
  void noRowIsEmpty() {
    when(repository.findByFeatureKeyOrderByRankAsc("agent.unknown")).thenReturn(List.of());

    assertEquals(List.of(), manager.modelNames("agent.unknown"));
  }

  @Test
  @DisplayName("a blank or null key never reaches the database")
  void blankKeyIsEmpty() {
    assertEquals(List.of(), manager.modelNames(null));
    assertEquals(List.of(), manager.modelNames("  "));
    verifyNoInteractions(repository);
  }
}
