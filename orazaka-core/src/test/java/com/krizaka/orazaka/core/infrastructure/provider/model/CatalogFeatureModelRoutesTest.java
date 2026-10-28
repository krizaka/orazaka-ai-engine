package com.krizaka.orazaka.core.infrastructure.provider.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.orazaka.persistence.domain.ports.inbound.FeatureRouteManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogFeatureModelRoutesTest {

  @Test
  void theRoutesAreTheManagersAnswerInItsOrder() {
    FeatureRouteManager manager = mock(FeatureRouteManager.class);
    when(manager.modelNames("agent.code.issue-to-pr")).thenReturn(List.of("a", "b"));

    assertEquals(
        List.of("a", "b"),
        new CatalogFeatureModelRoutes(manager).modelNames("agent.code.issue-to-pr"));
  }
}
