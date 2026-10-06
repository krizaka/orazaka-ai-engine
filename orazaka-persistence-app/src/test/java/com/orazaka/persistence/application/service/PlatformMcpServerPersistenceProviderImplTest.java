package com.orazaka.persistence.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.orazaka.persistence.domain.model.PlatformMcpServerDto;
import com.orazaka.persistence.infrastructure.adapter.persistence.entity.PlatformMcpServerEntity;
import com.orazaka.persistence.infrastructure.adapter.persistence.repository.PlatformMcpServerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformMcpServerPersistenceProviderImplTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Mock private PlatformMcpServerRepository repository;

  @InjectMocks private PlatformMcpServerPersistenceProviderImpl provider;

  @Test
  @DisplayName("findByEnabledTrue retrieves enabled servers")
  void findByEnabledTrue() {
    PlatformMcpServerEntity entity = new PlatformMcpServerEntity();
    entity.setId(1);
    entity.setLabel("label");
    entity.setEnabled(true);
    entity.setTransportType("SSE");

    when(repository.findByEnabledTrue()).thenReturn(List.of(entity));

    List<PlatformMcpServerDto> result = provider.findByEnabledTrue();

    assertEquals(1, result.size());
    assertTrue(result.get(0).enabled());
  }

  @Test
  @DisplayName("findAll retrieves all servers")
  void findAll() {
    PlatformMcpServerEntity entity = new PlatformMcpServerEntity();
    entity.setId(1);
    entity.setLabel("label");
    entity.setEnabled(true);
    entity.setTransportType("SSE");

    when(repository.findAll()).thenReturn(List.of(entity));

    List<PlatformMcpServerDto> result = provider.findAll();

    assertEquals(1, result.size());
  }

  @Test
  @DisplayName("findById retrieves server by ID")
  void findById() {
    PlatformMcpServerEntity entity = new PlatformMcpServerEntity();
    entity.setId(1);
    entity.setLabel("label");
    entity.setEnabled(true);
    entity.setTransportType("SSE");

    when(repository.findById(1)).thenReturn(Optional.of(entity));

    Optional<PlatformMcpServerDto> result = provider.findById(1);

    assertTrue(result.isPresent());
    assertEquals(1, result.get().id());
  }

  @Test
  @DisplayName("save persists server details")
  void save() {
    Instant now = Instant.now(FIXED_CLOCK);
    PlatformMcpServerDto dto =
        new PlatformMcpServerDto(1, "MCP", "SSE", "url", "cmd", "args", "token", true, now);
    PlatformMcpServerEntity entity = PlatformMcpServerPersistenceProviderImpl.toEntity(dto);

    when(repository.save(any(PlatformMcpServerEntity.class))).thenReturn(entity);

    PlatformMcpServerDto saved = provider.save(dto);

    assertNotNull(saved);
    assertEquals("MCP", saved.label());
  }

  @Test
  @DisplayName("deleteById deletes server")
  void deleteById() {
    doNothing().when(repository).deleteById(1);
    provider.deleteById(1);
    verify(repository).deleteById(1);
  }
}
