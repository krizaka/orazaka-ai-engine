package com.krizaka.orazaka.persistence.application.service;

import com.krizaka.orazaka.persistence.domain.model.UserMcpServerDto;
import com.krizaka.orazaka.persistence.domain.ports.inbound.UserMcpServerPersistenceProvider;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.entity.UserMcpServerEntity;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.repository.UserMcpServerRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Package-private implementation of UserMcpServerPersistenceProvider. */
@Service
class UserMcpServerPersistenceProviderImpl implements UserMcpServerPersistenceProvider {

  private final UserMcpServerRepository repository;

  UserMcpServerPersistenceProviderImpl(UserMcpServerRepository repository) {
    this.repository = Objects.requireNonNull(repository, "UserMcpServerRepository cannot be null");
  }

  @Override
  public List<UserMcpServerDto> findByUserIdAndEnabledTrue(String userId) {
    Objects.requireNonNull(userId, "userId cannot be null");
    return repository.findByUserIdAndEnabledTrue(userId).stream()
        .map(UserMcpServerPersistenceProviderImpl::toDto)
        .toList();
  }

  @Override
  public List<UserMcpServerDto> findByUserId(String userId) {
    Objects.requireNonNull(userId, "userId cannot be null");
    return repository.findByUserId(userId).stream()
        .map(UserMcpServerPersistenceProviderImpl::toDto)
        .toList();
  }

  @Override
  public Optional<UserMcpServerDto> findById(Integer id) {
    Objects.requireNonNull(id, "id cannot be null");
    return repository.findById(id).map(UserMcpServerPersistenceProviderImpl::toDto);
  }

  @Override
  public UserMcpServerDto save(UserMcpServerDto serverDto) {
    Objects.requireNonNull(serverDto, "UserMcpServerDto cannot be null");
    UserMcpServerEntity entity = toEntity(serverDto);
    UserMcpServerEntity saved = repository.save(entity);
    return toDto(saved);
  }

  @Override
  public void deleteById(Integer id) {
    Objects.requireNonNull(id, "id cannot be null");
    repository.deleteById(id);
  }

  private static UserMcpServerDto toDto(UserMcpServerEntity entity) {
    if (entity == null) {
      return null;
    }
    return new UserMcpServerDto(
        entity.getId(),
        entity.getUserId(),
        entity.getLabel(),
        entity.getUrl(),
        entity.getAuthToken(),
        entity.getEnabled(),
        entity.getCreatedAt());
  }

  static UserMcpServerEntity toEntity(UserMcpServerDto dto) {
    if (dto == null) {
      return null;
    }
    UserMcpServerEntity entity = new UserMcpServerEntity();
    entity.setId(dto.id());
    entity.setUserId(dto.userId());
    entity.setLabel(dto.label());
    entity.setUrl(dto.url());
    entity.setAuthToken(dto.authToken());
    entity.setEnabled(dto.enabled());
    entity.setCreatedAt(dto.createdAt());
    return entity;
  }
}
