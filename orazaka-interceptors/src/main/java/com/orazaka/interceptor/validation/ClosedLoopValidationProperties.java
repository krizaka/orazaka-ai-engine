package com.orazaka.interceptor.validation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Closed-loop validation Advisor.
 *
 * <p>Controls the autonomous 4-tier self-correction chain: Tier A (Deterministic JSON Schema), Tier
 * B (MCP Sandbox Crash-Test), Tier C (Semantic Consensus Debate), and Tier D (Test-Driven Response
 * via System MCP).
 *
 * @param enabled Whether the quantum validation chain is active.
 * @param maxRetries Maximum number of correction retries before yielding.
 * @param schemaStrict Whether Tier A enforces strict JSON schema compliance.
 * @param sandboxEnabled Whether Tier B (MCP sandbox compilation) is active.
 * @param debateEnabled Whether Tier C (Critic vs Advocate semantic debate) is active.
 * @param tdrEnabled Whether Tier D (Test-Driven Response assertion generation) is active.
 */
@ConfigurationProperties(prefix = "orazaka.interceptor.validation.closed-loop")
public record ClosedLoopValidationProperties(
    boolean enabled,
    int maxRetries,
    boolean schemaStrict,
    boolean sandboxEnabled,
    boolean debateEnabled,
    boolean tdrEnabled) {

  /** Canonical constructor with validation and defaults. */
  public ClosedLoopValidationProperties {
    if (maxRetries < 0) {
      maxRetries = 0;
    }
    if (maxRetries > 5) {
      maxRetries = 5;
    }
  }

  /** Default configuration — all tiers active (Tier D disabled by default), 3 retry budget. */
  public static ClosedLoopValidationProperties defaults() {
    return new ClosedLoopValidationProperties(true, 3, true, true, true, false);
  }
}
