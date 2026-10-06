package com.orazaka.persistence.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.orazaka.core.domain.model.InterceptorConfig;
import com.orazaka.persistence.domain.model.InterceptorConfigDto;
import com.orazaka.persistence.domain.ports.inbound.PipelineConfigManager;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineConfigProviderAdapterTest {

  private final PipelineConfigManager manager = mock(PipelineConfigManager.class);
  private final PipelineConfigProviderAdapter provider = new PipelineConfigProviderAdapter(manager);

  @Test
  void findAllOrdered_mapsDtosToRecords() {
    when(manager.findAllOrdered())
        .thenReturn(List.of(new InterceptorConfigDto("MEMORY", "Memory", 5, true, "Memory")));
    List<InterceptorConfig> result = provider.findAllOrdered();
    assertEquals(1, result.size());
    assertEquals("MEMORY", result.get(0).interceptorKey());
    assertEquals(5, result.get(0).executionOrder());
    assertTrue(result.get(0).enabled());
  }

  @Test
  void findAllOrdered_emptyList() {
    when(manager.findAllOrdered()).thenReturn(List.of());
    assertTrue(provider.findAllOrdered().isEmpty());
  }

  @Test
  void save_mapsRecordToDtoAndBack() {
    var config = new InterceptorConfig("ROUTER", "Router", 7, true, "Router interceptor");
    when(manager.save(any())).thenAnswer(inv -> inv.getArgument(0));
    InterceptorConfig result = provider.save(config);
    assertEquals("ROUTER", result.interceptorKey());
    verify(manager).save(any(InterceptorConfigDto.class));
  }

  @Test
  void saveAll_delegatesAndMaps() {
    when(manager.saveAll(anyList()))
        .thenReturn(List.of(new InterceptorConfigDto("ROUTER", "Router", 7, true, "d")));
    List<InterceptorConfig> result =
        provider.saveAll(List.of(new InterceptorConfig("ROUTER", "Router", 7, true, "d")));
    assertEquals(1, result.size());
    assertEquals("ROUTER", result.get(0).interceptorKey());
  }

  @Test
  void resetToDefaults_delegates() {
    provider.resetToDefaults(List.of(new InterceptorConfig("ROUTER", "Router", 7, true, "d")));
    verify(manager).resetToDefaults(anyList());
  }

  @Test
  void constructor_nullManager_throws() {
    assertThrows(NullPointerException.class, () -> new PipelineConfigProviderAdapter(null));
  }
}
