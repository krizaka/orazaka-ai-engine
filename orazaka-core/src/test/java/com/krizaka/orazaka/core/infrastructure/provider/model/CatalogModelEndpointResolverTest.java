package com.krizaka.orazaka.core.infrastructure.provider.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.orazaka.core.domain.model.ModelEndpoint;
import com.krizaka.orazaka.persistence.domain.model.CatalogModelDto;
import com.krizaka.orazaka.persistence.domain.ports.inbound.CatalogModelManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The endpoint of a catalogue model carries its provider's base URL and API key (ADR-030). */
class CatalogModelEndpointResolverTest {

  private static CatalogModelDto model(String name, String provider) {
    CatalogModelDto dto = mock(CatalogModelDto.class);
    when(dto.modelName()).thenReturn(name);
    when(dto.providerName()).thenReturn(provider);
    return dto;
  }

  @Test
  void hostedProvider_endpointCarriesItsKey() {
    CatalogModelManager catalog = mock(CatalogModelManager.class);
    CatalogModelDto mistral = model("mistral-3-14B", "do-inference");
    when(catalog.getDefaultModelByCategory("chat")).thenReturn(Optional.of(mistral));
    when(catalog.getProviderBaseUrl("do-inference")).thenReturn("https://inference.do-ai.run");
    when(catalog.getProviderApiKey("do-inference")).thenReturn("sk-test");

    ModelEndpoint endpoint =
        new CatalogModelEndpointResolver(catalog).resolveDefault("chat").orElseThrow();

    assertEquals("https://inference.do-ai.run", endpoint.baseUrl());
    assertEquals("sk-test", endpoint.apiKey());
    assertEquals("mistral-3-14B", endpoint.modelName());
  }

  @Test
  void localProvider_keepsTheKeylessDefault() {
    CatalogModelManager catalog = mock(CatalogModelManager.class);
    CatalogModelDto qwen = model("qwen2.5:7b", "ollama");
    when(catalog.getAllModels()).thenReturn(List.of(qwen));
    when(catalog.getProviderBaseUrl("ollama")).thenReturn("http://localhost:11434");

    ModelEndpoint endpoint =
        new CatalogModelEndpointResolver(catalog).resolve("qwen2.5:7b").orElseThrow();

    assertEquals("not-required", endpoint.apiKey()); // ModelEndpoint's value for a keyless provider
  }
}
