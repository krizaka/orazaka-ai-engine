package com.krizaka.orazaka.persistence.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.orazaka.persistence.domain.model.CatalogModelDto;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.entity.AiProviderEntity;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.entity.CatalogModelEntity;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.repository.AiProviderRepository;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.repository.CatalogModelRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogModelManagerImplTest {

  @Mock private CatalogModelRepository repository;
  @Mock private AiProviderRepository providerRepository;

  @InjectMocks private CatalogModelManagerImpl manager;

  @Test
  @DisplayName("getAllModels retrieves and maps all models")
  void getAllModels() {
    CatalogModelEntity entity = new CatalogModelEntity();
    entity.setId(1);
    entity.setModelName("m");
    entity.setModelLabel("l");
    entity.setCategory("c");
    entity.setOptions("{}");
    entity.setIsDefault(true);
    entity.setProviderName("p");
    entity.setMaxSteps(10);
    entity.setRecommendedFps(24);
    entity.setSupportedHardware("GPU");

    when(repository.findAll()).thenReturn(List.of(entity));

    List<CatalogModelDto> result = manager.getAllModels();

    assertEquals(1, result.size());
    assertEquals("m", result.get(0).modelName());
  }

  @Test
  @DisplayName("getModelsByCategory retrieves and maps models by category")
  void getModelsByCategory() {
    CatalogModelEntity entity = new CatalogModelEntity();
    entity.setId(1);
    entity.setModelName("m");
    entity.setModelLabel("l");
    entity.setCategory("c");
    entity.setOptions("{}");
    entity.setIsDefault(true);
    entity.setProviderName("p");
    entity.setMaxSteps(10);
    entity.setRecommendedFps(24);
    entity.setSupportedHardware("GPU");

    when(repository.findByCategory("c")).thenReturn(List.of(entity));

    List<CatalogModelDto> result = manager.getModelsByCategory("c");

    assertEquals(1, result.size());
    assertEquals("c", result.get(0).category());
  }

  @Test
  @DisplayName("getDefaultModelByCategory returns mapped default model if present")
  void getDefaultModelByCategory() {
    CatalogModelEntity entity = new CatalogModelEntity();
    entity.setId(1);
    entity.setModelName("m");
    entity.setModelLabel("l");
    entity.setCategory("c");
    entity.setOptions("{}");
    entity.setIsDefault(true);
    entity.setProviderName("p");
    entity.setMaxSteps(10);
    entity.setRecommendedFps(24);
    entity.setSupportedHardware("GPU");

    when(repository.findByCategoryAndIsDefaultTrue("c")).thenReturn(Optional.of(entity));

    Optional<CatalogModelDto> result = manager.getDefaultModelByCategory("c");

    assertTrue(result.isPresent());
    assertTrue(result.get().isDefault());
  }

  @Test
  @DisplayName("saveModel saves model and toggles other defaults if necessary")
  void saveModel() {
    CatalogModelDto dto = new CatalogModelDto(1, "m", "l", "c", "{}", true, "p", 10, 24, "GPU");
    CatalogModelEntity otherEntity = new CatalogModelEntity();
    otherEntity.setId(2);
    otherEntity.setCategory("c");
    otherEntity.setIsDefault(true);

    when(repository.findByCategory("c")).thenReturn(List.of(otherEntity));
    when(repository.save(any(CatalogModelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    CatalogModelDto saved = manager.saveModel(dto);

    assertNotNull(saved);
    assertTrue(saved.isDefault());
    verify(repository).save(otherEntity);
    assertFalse(otherEntity.getIsDefault());
  }

  @Test
  @DisplayName("deleteModel deletes model by ID")
  void deleteModel() {
    doNothing().when(repository).deleteById(1);
    manager.deleteModel(1);
    verify(repository).deleteById(1);
  }

  @Test
  @DisplayName("getAllProviders returns all provider names")
  void getAllProviders() {
    AiProviderEntity provider = new AiProviderEntity();
    provider.setName("p");
    provider.setBaseUrl("url");

    when(providerRepository.findAll()).thenReturn(List.of(provider));

    List<String> result = manager.getAllProviders();

    assertEquals(1, result.size());
    assertEquals("p", result.get(0));
  }

  @Test
  @DisplayName("getProviderBaseUrl returns base URL for provider")
  void getProviderBaseUrl() {
    AiProviderEntity provider = new AiProviderEntity();
    provider.setName("p");
    provider.setBaseUrl("url");

    when(providerRepository.findByName("p")).thenReturn(Optional.of(provider));

    String url = manager.getProviderBaseUrl("p");

    assertEquals("url", url);
  }
}
