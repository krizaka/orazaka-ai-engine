package com.krizaka.orazaka.core.domain.model.checks;

/**
 * What an external check is for (orazaka-ai-engine#73). Consent is given per purpose, and the
 * pricebook prices each one.
 */
public enum CheckPurpose {
  /** Court records: the TAL and the civil and penal plumitifs. */
  COURT_RECORDS,
  /** A credit report. */
  CREDIT,
  /** Identity verification. */
  IDENTITY
}
