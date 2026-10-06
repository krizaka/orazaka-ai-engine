package com.orazaka.core.domain.ports.outbound;

import com.orazaka.core.domain.model.ModelEndpoint;
import java.util.Optional;

/**
 * Outbound port (ADR-030): resolves a model to its DB-backed {@link ModelEndpoint} — the provider,
 * base-url and api-key needed to build a client. The implementing adapter reads {@code
 * orazaka_models} (model → {@code provider_name}, {@code is_default}) and {@code ai_providers}
 * (provider → base-url/api-key), keeping {@code orazaka-core} free of any persistence import.
 */
public interface ModelEndpointResolver {

  /** Resolves the endpoint for an explicit model name. */
  Optional<ModelEndpoint> resolve(String modelName);

  /** Resolves the endpoint for a capability category's default model ({@code is_default}). */
  Optional<ModelEndpoint> resolveDefault(String category);
}
