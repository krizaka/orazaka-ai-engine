package com.krizaka.orazaka.core.domain.ports.inbound;

import com.krizaka.orazaka.core.domain.model.research.GroundedAnswer;
import com.krizaka.orazaka.core.domain.model.research.VerifyMode;

/**
 * Checks every claim of a grounded answer against the stored bytes it cites (ADR-087 §3).
 * Deterministic: no model is asked whether a quote is right.
 */
public interface CitationVerifier {

  /**
   * The answer with each claim's status set from what was read.
   *
   * @param answer the model's answer in the citation shape
   * @param mode keep unverified claims marked, or drop them
   * @return the verified answer
   */
  GroundedAnswer verify(GroundedAnswer answer, VerifyMode mode);
}
