package com.krizaka.orazaka.persistence.application.service;

import com.krizaka.orazaka.persistence.domain.model.PlatformMcpServerDto;
import com.krizaka.orazaka.persistence.domain.ports.inbound.PlatformMcpServerPersistenceProvider;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.entity.PlatformMcpServerEntity;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.repository.PlatformMcpServerRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Package-private implementation of PlatformMcpServerPersistenceProvider. */
@Service
class PlatformMcpServerPersistenceProviderImpl implements PlatformMcpServerPersistenceProvider {

  private final PlatformMcpServerRepository repository;

  PlatformMcpServerPersistenceProviderImpl(PlatformMcpServerRepository repository) {
    this.repository =
        Objects.requireNonNull(repository, "PlatformMcpServerRepository cannot be null");
  }

  @Override
  public List<PlatformMcpServerDto> findByEnabledTrue() {
    return repository.findByEnabledTrue().stream()
        .map(PlatformMcpServerPersistenceProviderImpl::toDto)
        .toList();
  }

  @Override
  public List<PlatformMcpServerDto> findAll() {
    return repository.findAll().stream()
        .map(PlatformMcpServerPersistenceProviderImpl::toDto)
        .toList();
  }

  @Override
  public Optional<PlatformMcpServerDto> findById(Integer id) {
    Objects.requireNonNull(id, "id cannot be null");
    return repository.findById(id).map(PlatformMcpServerPersistenceProviderImpl::toDto);
  }

  @Override
  public PlatformMcpServerDto save(PlatformMcpServerDto serverDto) {
    Objects.requireNonNull(serverDto, "PlatformMcpServerDto cannot be null");
    PlatformMcpServerEntity entity = toEntity(serverDto);
    PlatformMcpServerEntity saved = repository.save(entity);
    return toDto(saved);
  }

  @Override
  public void deleteById(Integer id) {
    Objects.requireNonNull(id, "id cannot be null");
    repository.deleteById(id);
  }

  private static PlatformMcpServerDto toDto(PlatformMcpServerEntity entity) {
    if (entity == null) {
      return null;
    }
    return new PlatformMcpServerDto(
        entity.getId(),
        entity.getLabel(),
        entity.getTransportType(),
        entity.getUrl(),
        entity.getCommand(),
        entity.getArgs(),
        entity.getAuthToken(),
        entity.getEnabled(),
        entity.getCreatedAt());
  }

  static PlatformMcpServerEntity toEntity(PlatformMcpServerDto dto) {
    if (dto == null) {
      return null;
    }
    PlatformMcpServerEntity entity = new PlatformMcpServerEntity();
    entity.setId(dto.id());
    entity.setLabel(dto.label());
    entity.setTransportType(dto.transportType());
    entity.setUrl(dto.url());
    entity.setCommand(dto.command());
    entity.setArgs(dto.args());
    entity.setAuthToken(dto.authToken());
    entity.setEnabled(dto.enabled());
    entity.setCreatedAt(dto.createdAt());
    return entity;
  }
}
