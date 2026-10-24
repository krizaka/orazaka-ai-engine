package com.krizaka.orazaka.core.domain.model.checks;

import java.util.Objects;
import java.util.UUID;

/**
 * One external check asked for by an actor.
 *
 * @param userId the actor, whose own key is used when there is one; {@code null} for a platform
 *     call
 * @param runId the Studio run it serves, or {@code null}
 * @param subject the person checked
 */
public record CheckCall(String userId, UUID runId, CheckSubject subject) {

  /** Validates the call. */
  public CheckCall {
    Objects.requireNonNull(subject, "subject must not be null");
  }
}
