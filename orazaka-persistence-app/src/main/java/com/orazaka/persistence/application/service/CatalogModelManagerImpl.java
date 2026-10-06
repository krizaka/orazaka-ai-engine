package com.orazaka.persistence.application.service;

import com.orazaka.persistence.domain.model.CatalogModelDto;
import com.orazaka.persistence.domain.ports.inbound.CatalogModelManager;
import com.orazaka.persistence.infrastructure.adapter.persistence.entity.AiProviderEntity;
import com.orazaka.persistence.infrastructure.adapter.persistence.entity.CatalogModelEntity;
import com.orazaka.persistence.infrastructure.adapter.persistence.repository.AiProviderRepository;
import com.orazaka.persistence.infrastructure.adapter.persistence.repository.CatalogModelRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Package-private implementation of the CatalogModelManager inbound port. */
@Service
@Transactional
class CatalogModelManagerImpl implements CatalogModelManager {

  private final CatalogModelRepository repository;
  private final AiProviderRepository providerRepository;

  CatalogModelManagerImpl(
      CatalogModelRepository repository, AiProviderRepository providerRepository) {
    this.repository = Objects.requireNonNull(repository, "CatalogModelRepository cannot be null");
    this.providerRepository =
        Objects.requireNonNull(providerRepository, "AiProviderRepository cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "catalogModels", key = "'all'")
  public List<CatalogModelDto> getAllModels() {
    return repository.findAll().stream().map(CatalogModelManagerImpl::toDto).toList();
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "catalogModels", key = "#category")
  public List<CatalogModelDto> getModelsByCategory(String category) {
    Objects.requireNonNull(category, "Category cannot be null");
    return repository.findByCategory(category).stream()
        .map(CatalogModelManagerImpl::toDto)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(value = "catalogModelDefault", key = "#category")
  public Optional<CatalogModelDto> getDefaultModelByCategory(String category) {
    Objects.requireNonNull(category, "Category cannot be null");
    return repository.findByCategoryAndIsDefaultTrue(category).map(CatalogModelManagerImpl::toDto);
  }

  @Override
  @CacheEvict(
      value = {"catalogModels", "catalogModelDefault"},
      allEntries = true)
  public CatalogModelDto saveModel(CatalogModelDto dto) {
    Objects.requireNonNull(dto, "CatalogModelDto cannot be null");

    // If the new/updated model is marked as default, unset other defaults in the same category
    if (Boolean.TRUE.equals(dto.isDefault())) {
      repository
          .findByCategory(dto.category())
          .forEach(
              entity -> {
                if (Boolean.TRUE.equals(entity.getIsDefault())
                    && !Objects.equals(entity.getId(), dto.id())) {
                  entity.setIsDefault(false);
                  repository.save(entity);
                }
              });
    }

    CatalogModelEntity entity = toEntity(dto);
    CatalogModelEntity saved = repository.save(entity);
    return toDto(saved);
  }

  @Override
  @CacheEvict(
      value = {"catalogModels", "catalogModelDefault"},
      allEntries = true)
  public void deleteModel(Integer id) {
    Objects.requireNonNull(id, "ID cannot be null");
    repository.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> getAllProviders() {
    return providerRepository.findAll().stream().map(AiProviderEntity::getName).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public String getProviderBaseUrl(String providerName) {
    return providerRepository
        .findByName(providerName)
        .map(AiProviderEntity::getBaseUrl)
        .orElse(null);
  }

  private static CatalogModelDto toDto(CatalogModelEntity entity) {
    if (entity == null) {
      return null;
    }
    return new CatalogModelDto(
        entity.getId(),
        entity.getModelName(),
        entity.getModelLabel(),
        entity.getCategory(),
        entity.getOptions(),
        entity.getIsDefault(),
        entity.getProviderName(),
        entity.getMaxSteps(),
        entity.getRecommendedFps(),
        entity.getSupportedHardware(),
        entity.getDescription());
  }

  static CatalogModelEntity toEntity(CatalogModelDto dto) {
    if (dto == null) {
      return null;
    }
    CatalogModelEntity entity = new CatalogModelEntity();
    entity.setId(dto.id());
    entity.setModelName(dto.modelName());
    entity.setModelLabel(dto.modelLabel());
    entity.setCategory(dto.category());
    entity.setOptions(dto.options());
    entity.setIsDefault(dto.isDefault());
    entity.setProviderName(dto.providerName());
    entity.setMaxSteps(dto.maxSteps());
    entity.setRecommendedFps(dto.recommendedFps());
    entity.setSupportedHardware(dto.supportedHardware());
    entity.setDescription(dto.description());
    return entity;
  }
}
