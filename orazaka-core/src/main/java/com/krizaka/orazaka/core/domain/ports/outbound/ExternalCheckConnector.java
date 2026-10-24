package com.krizaka.orazaka.core.domain.ports.outbound;

import com.krizaka.orazaka.core.domain.model.checks.CheckRequest;
import com.krizaka.orazaka.core.domain.model.checks.CheckResult;

/**
 * An external provider of checks: court records, credit, identity (orazaka-ai-engine#73). A
 * connector only calls its provider with the key it is given. Consent, the choice of key and the
 * audit are the service's, never the connector's.
 *
 * <p>The keys are read from the encrypted settings by the key store, never from an environment
 * variable.
 */
public interface ExternalCheckConnector {

  /**
   * The provider's name, as the audit and the admin settings name it.
   *
   * @return e.g. {@code soquij}, {@code trustii}, {@code certn}, {@code mock}
   */
  String provider();

  /**
   * Searches the court records (purpose {@code COURT_RECORDS}).
   *
   * @param request the subject and the resolved key
   * @return the answer; never {@code REFUSED}
   */
  CheckResult verifyCourtRecords(CheckRequest request);

  /**
   * Reads a credit report (purpose {@code CREDIT}).
   *
   * @param request the subject and the resolved key
   * @return the answer; never {@code REFUSED}
   */
  CheckResult verifyCredit(CheckRequest request);

  /**
   * Verifies the identity (purpose {@code IDENTITY}).
   *
   * @param request the subject and the resolved key
   * @return the answer; never {@code REFUSED}
   */
  CheckResult verifyIdentity(CheckRequest request);
}
