package com.orazaka.core.domain.model.job;

/**
 * Type-safe enumeration of all legal job lifecycle states. Replaces raw string constants
 * ("PENDING", "PROCESSING", etc.) across the codebase with compile-time guarantees.
 *
 * <p>Persistence adapters must convert to/from {@code String} via {@link #name()} and {@link
 * #fromString(String)}.
 */
public enum JobStatus {
  PENDING,
  PROCESSING,
  COMPLETED,
  FAILED;

  /**
   * Parses a case-insensitive status string into a {@code JobStatus}.
   *
   * @param value the raw status string from persistence or API layers
   * @return the matching {@code JobStatus}
   * @throws IllegalArgumentException if the value does not match any known status
   */
  public static JobStatus fromString(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Job status cannot be null or blank");
    }
    return valueOf(value.toUpperCase());
  }
}
