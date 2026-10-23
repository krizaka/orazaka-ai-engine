package com.krizaka.orazaka.core.domain.model.research;

/** Why a claim is {@link ClaimStatus#TO_VALIDATE}. */
public enum ClaimReason {
  /** The claim cites nothing. */
  NO_SOURCE,
  /** No quote of the claim appears in the document it names. */
  QUOTE_NOT_FOUND,
  /** The cited URL and hash name nothing research read. */
  URL_UNRESOLVED,
  /** The source the claim needs is disabled. */
  SOURCE_DISABLED,
  /** A URL, a repository or a statute in the answer that no verified claim cites. */
  UNLISTED_REFERENCE
}
