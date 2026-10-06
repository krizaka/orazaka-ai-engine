package com.orazaka.core.domain.model;

/**
 * Immutable security role representation for the Orazaka ecosystem.
 *
 * @param name The name of the authority/role (e.g., "ROLE_USER").
 */
public record Authority(String name) {
  public Authority {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Authority name cannot be null or empty");
    }
  }
}
