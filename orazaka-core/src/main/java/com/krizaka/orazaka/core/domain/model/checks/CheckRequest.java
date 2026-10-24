package com.krizaka.orazaka.core.domain.model.checks;

import java.util.Objects;

/**
 * What a connector receives: the subject and the key already resolved for this call.
 *
 * @param subject the person checked
 * @param key the provider key to call with
 */
public record CheckRequest(CheckSubject subject, CheckKey key) {

  /** Validates the request. */
  public CheckRequest {
    Objects.requireNonNull(subject, "subject must not be null");
    Objects.requireNonNull(key, "key must not be null");
  }
}
