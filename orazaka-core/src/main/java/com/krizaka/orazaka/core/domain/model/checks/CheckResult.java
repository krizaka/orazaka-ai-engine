package com.krizaka.orazaka.core.domain.model.checks;

import java.util.Objects;

/**
 * The outcome of one external check. It carries no record content: a match is an outcome, not data.
 *
 * @param outcome what the check answered
 * @param refusal why it was refused, present exactly when the outcome is {@link
 *     CheckOutcome#REFUSED}
 * @param providerRef the provider's own reference for the case, or {@code null}
 */
public record CheckResult(CheckOutcome outcome, CheckRefusal refusal, String providerRef) {

  /** Validates that a refusal comes with {@link CheckOutcome#REFUSED} and nothing else does. */
  public CheckResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
    if ((outcome == CheckOutcome.REFUSED) != (refusal != null)) {
      throw new IllegalArgumentException(
          "a refusal comes with REFUSED, and REFUSED with a refusal");
    }
  }

  /**
   * An answer from the provider.
   *
   * @param outcome MATCH, NO_MATCH, PROVIDER_ERROR or TIMEOUT
   * @param providerRef the provider's reference, or {@code null}
   * @return the result
   */
  public static CheckResult answered(CheckOutcome outcome, String providerRef) {
    if (outcome == CheckOutcome.REFUSED) {
      throw new IllegalArgumentException("use refused() for a refusal");
    }
    return new CheckResult(outcome, null, providerRef);
  }

  /**
   * A call refused before any provider call.
   *
   * @param refusal why
   * @return the result
   */
  public static CheckResult refused(CheckRefusal refusal) {
    return new CheckResult(CheckOutcome.REFUSED, Objects.requireNonNull(refusal), null);
  }
}
