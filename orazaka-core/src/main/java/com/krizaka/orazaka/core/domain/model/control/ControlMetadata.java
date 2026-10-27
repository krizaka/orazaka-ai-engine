package com.krizaka.orazaka.core.domain.model.control;

/**
 * The keys and values under which a control verdict travels in the metadata of a response, for the
 * UI (orazaka-ai-engine#125). A streamed chunk says {@link #PENDING} (« en vérification ») until
 * the final event carries the verdict.
 */
public interface ControlMetadata {

  /** Metadata key of the verdict state. */
  String VERDICT = "control.verdict";

  /** Metadata key of the badge a degraded verification must show. */
  String BADGE = "control.badge";

  /** Streamed chunk: the answer is still being verified. */
  String PENDING = "PENDING";

  /** No control screened the answer. */
  String NOT_CHECKED = "NOT_CHECKED";

  /** Every active check passed. */
  String VALIDATED = "VALIDATED";

  /** Served on the deterministic tiers alone, the judge being unavailable. */
  String DEGRADED = "DEGRADED";

  /** The badge of a {@link #DEGRADED} answer: « vérification sémantique indisponible ». */
  String BADGE_VERIFICATION_UNAVAILABLE = "verification_unavailable";
}
