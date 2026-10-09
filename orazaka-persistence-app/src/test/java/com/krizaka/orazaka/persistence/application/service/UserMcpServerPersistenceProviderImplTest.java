package com.krizaka.orazaka.persistence.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.krizaka.orazaka.persistence.domain.model.UserMcpServerDto;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.entity.UserMcpServerEntity;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.repository.UserMcpServerRepository;
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
class UserMcpServerPersistenceProviderImplTest {
  private static final java.time.Clock FIXED_CLOCK =
      java.time.Clock.fixed(
          java.time.Instant.parse("2026-01-01T00:00:00Z"), java.time.ZoneOffset.UTC);

  @Mock private UserMcpServerRepository repository;

  @InjectMocks private UserMcpServerPersistenceProviderImpl provider;

  @Test
  @DisplayName("Constructor throws NullPointerException on null repository")
  void constructorValidation() {
    assertThatThrownBy(() -> new UserMcpServerPersistenceProviderImpl(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("UserMcpServerRepository cannot be null");
  }

  @Test
  @DisplayName("findByUserIdAndEnabledTrue retrieves and maps entities")
  void findByUserIdAndEnabledTrue() {
    UserMcpServerEntity entity = new UserMcpServerEntity();
    entity.setId(1);
    entity.setUserId("user-1");
    entity.setLabel("mcp-server");
    entity.setUrl("http://localhost");
    entity.setAuthToken("token");
    entity.setEnabled(true);
    entity.setCreatedAt(Instant.now(FIXED_CLOCK));

    when(repository.findByUserIdAndEnabledTrue("user-1")).thenReturn(List.of(entity));

    List<UserMcpServerDto> result = provider.findByUserIdAndEnabledTrue("user-1");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).label()).isEqualTo("mcp-server");
    verify(repository).findByUserIdAndEnabledTrue("user-1");
  }

  @Test
  @DisplayName("findByUserId retrieves and maps entities")
  void findByUserId() {
    UserMcpServerEntity entity = new UserMcpServerEntity();
    entity.setId(1);
    entity.setUserId("user-1");
    entity.setLabel("mcp-server");
    entity.setUrl("http://localhost");
    entity.setEnabled(true);

    when(repository.findByUserId("user-1")).thenReturn(List.of(entity));

    List<UserMcpServerDto> result = provider.findByUserId("user-1");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).label()).isEqualTo("mcp-server");
  }

  @Test
  @DisplayName("findById retrieves and maps entity if present")
  void findById() {
    UserMcpServerEntity entity = new UserMcpServerEntity();
    entity.setId(1);
    entity.setUserId("user-1");
    entity.setLabel("mcp-server");
    entity.setUrl("http://localhost");
    entity.setEnabled(true);

    when(repository.findById(1)).thenReturn(Optional.of(entity));

    Optional<UserMcpServerDto> result = provider.findById(1);

    assertThat(result).isPresent();
    assertThat(result.get().label()).isEqualTo("mcp-server");
  }

  @Test
  @DisplayName("save persists and maps UserMcpServerDto")
  void save() {
    UserMcpServerDto dto =
        new UserMcpServerDto(
            1, "user-1", "mcp-server", "http://localhost", "token", true, Instant.now(FIXED_CLOCK));
    UserMcpServerEntity entity = UserMcpServerPersistenceProviderImpl.toEntity(dto);
    when(repository.save(any(UserMcpServerEntity.class))).thenReturn(entity);

    UserMcpServerDto result = provider.save(dto);

    assertThat(result).isNotNull();
    assertThat(result.label()).isEqualTo("mcp-server");
    verify(repository).save(any(UserMcpServerEntity.class));
  }

  @Test
  @DisplayName("deleteById deletes entity")
  void delete() {
    doNothing().when(repository).deleteById(1);

    provider.deleteById(1);

    verify(repository).deleteById(1);
  }
}
