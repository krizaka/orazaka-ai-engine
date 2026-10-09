package com.krizaka.orazaka.core.infrastructure.provider.model;

import com.krizaka.orazaka.core.domain.model.ModelEndpoint;
import com.krizaka.orazaka.core.domain.ports.outbound.ModelEndpointResolver;
import com.krizaka.orazaka.core.infrastructure.provider.DynamicModelRouter;
import com.krizaka.orazaka.persistence.domain.model.CatalogModelDto;
import com.krizaka.orazaka.persistence.domain.ports.inbound.CatalogModelManager;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * DB-backed {@link ModelEndpointResolver} adapter (ADR-030).
 *
 * <p>Reads {@code orazaka_models} (model → {@code provider_name}, {@code is_default} per category)
 * and {@code ai_providers} (provider → base-url) through the {@link CatalogModelManager} inbound
 * port, so {@code orazaka-core}'s {@link DynamicModelRouter} stays free of any persistence import.
 * The api-key is {@code "not-required"} here: the Universal Proxy targets the OpenAI-compatible
 * <b>local</b> providers (Ollama/LocalAI); commercial per-user keys flow through the engine's
 * {@code ModelFactory} path instead.
 */
@Component
class CatalogModelEndpointResolver implements ModelEndpointResolver {

  private final CatalogModelManager catalog;

  CatalogModelEndpointResolver(CatalogModelManager catalog) {
    this.catalog = Objects.requireNonNull(catalog, "CatalogModelManager cannot be null");
  }

  @Override
  public Optional<ModelEndpoint> resolve(String modelName) {
    if (modelName == null || modelName.isBlank()) {
      return Optional.empty();
    }
    return catalog.getAllModels().stream()
        .filter(m -> modelName.equalsIgnoreCase(m.modelName()))
        .findFirst()
        .flatMap(this::toEndpoint);
  }

  @Override
  public Optional<ModelEndpoint> resolveDefault(String category) {
    return catalog.getDefaultModelByCategory(category).flatMap(this::toEndpoint);
  }

  private Optional<ModelEndpoint> toEndpoint(CatalogModelDto model) {
    String baseUrl = catalog.getProviderBaseUrl(model.providerName());
    if (baseUrl == null || baseUrl.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(new ModelEndpoint(model.providerName(), baseUrl, null, model.modelName()));
  }
}
