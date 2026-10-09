package com.krizaka.orazaka.core.application.routing;

/**
 * Provider metrics — real-time health and performance data for deterministic routing.
 *
 * @param healthScore Resilience4j circuit-breaker health score (0.0–1.0).
 * @param p95LatencyMs 95th percentile latency in milliseconds.
 * @param providerVector Embedding vector for intent similarity scoring.
 */
public record ProviderMetrics(double healthScore, double p95LatencyMs, float[] providerVector) {

  public ProviderMetrics {
    if (healthScore < 0.0 || healthScore > 1.0) {
      throw new IllegalArgumentException("healthScore must be in [0.0, 1.0], got: " + healthScore);
    }
    if (p95LatencyMs < 0.0) {
      throw new IllegalArgumentException("p95LatencyMs must be non-negative, got: " + p95LatencyMs);
    }
    if (providerVector == null || providerVector.length == 0) {
      throw new IllegalArgumentException("providerVector must be non-null and non-empty");
    }
    providerVector = providerVector.clone();
  }

  @Override
  public float[] providerVector() {
    return providerVector.clone();
  }
}
