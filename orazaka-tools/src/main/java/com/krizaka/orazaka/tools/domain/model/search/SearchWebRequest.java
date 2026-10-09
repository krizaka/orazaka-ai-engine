package com.krizaka.orazaka.tools.domain.model.search;

import java.util.Objects;

/** Request payload for the searchWeb tool. */
public record SearchWebRequest(String query) {
  public SearchWebRequest {
    Objects.requireNonNull(query, "query must not be null");
    if (query.isBlank()) {
      throw new IllegalArgumentException("query must not be blank");
    }
  }
}
