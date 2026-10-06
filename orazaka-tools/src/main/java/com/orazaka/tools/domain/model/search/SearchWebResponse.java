package com.orazaka.tools.domain.model.search;

import java.util.Objects;

/** Response payload for the searchWeb tool. */
public record SearchWebResponse(String results, boolean success) {
  public SearchWebResponse {
    Objects.requireNonNull(results, "results must not be null");
  }
}
