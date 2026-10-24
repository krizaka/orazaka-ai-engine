package com.krizaka.orazaka.core.domain.model.checks;

/** Why an external check was refused before any provider call. */
public enum CheckRefusal {
  /** No consent is recorded for this subject and purpose, or it was withdrawn (Loi 25). */
  NO_CONSENT,
  /** Neither the actor nor the platform has a key for this provider. */
  NO_KEY,
  /** The run budget (or the credits) cannot hold the call: nothing is asked, nothing charged. */
  BUDGET_EXHAUSTED
}
