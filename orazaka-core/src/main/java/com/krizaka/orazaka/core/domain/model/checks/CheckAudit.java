package com.krizaka.orazaka.core.domain.model.checks;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of the external-check audit, written for every call, refused or not. It never holds a
 * name, a birth date, a court record or a credit figure: the subject is a hash of its id.
 *
 * @param at when
 * @param actorId the actor, or {@code null}
 * @param runId the run, or {@code null}
 * @param provider the connector's provider name
 * @param purpose what the check was for
 * @param keySource whose key was used, or {@code null} when refused before the key
 * @param outcome what it answered
 * @param refusal why it was refused, or {@code null}
 * @param subjectRef the SHA-256 (hex) of the subject id
 * @param durationMs the time spent, or {@code null} when no provider call was made
 * @param providerRef the provider's reference, or {@code null}
 */
public record CheckAudit(
    Instant at,
    String actorId,
    UUID runId,
    String provider,
    CheckPurpose purpose,
    KeySource keySource,
    CheckOutcome outcome,
    CheckRefusal refusal,
    String subjectRef,
    Long durationMs,
    String providerRef) {

  /** Validates the line. */
  public CheckAudit {
    Objects.requireNonNull(at, "at must not be null");
    Objects.requireNonNull(provider, "provider must not be null");
    Objects.requireNonNull(purpose, "purpose must not be null");
    Objects.requireNonNull(outcome, "outcome must not be null");
    Objects.requireNonNull(subjectRef, "subjectRef must not be null");
  }
}
