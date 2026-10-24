package com.krizaka.orazaka.core.application.checks;

import com.krizaka.orazaka.core.domain.model.checks.*;
import com.krizaka.orazaka.core.domain.ports.outbound.CheckAuditLog;
import com.krizaka.orazaka.core.domain.ports.outbound.CheckKeyStore;
import com.krizaka.orazaka.core.domain.ports.outbound.ConsentRegistry;
import com.krizaka.orazaka.core.domain.ports.outbound.ExternalCheckConnector;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * The one door to the external checks (orazaka-ai-engine#73). Every call, in this order:
 *
 * <ol>
 *   <li>consent for the purpose: none, or withdrawn, refuses without touching the provider;
 *   <li>the key: the actor's own, then the platform's; neither refuses;
 *   <li>the connector, which may answer an outcome or fail: a failure is a {@code PROVIDER_ERROR},
 *       and its message is neither returned nor audited;
 *   <li>one audit line, whatever happened.
 * </ol>
 *
 * <p>The connector is never reached without consent and a key, so no route can skip either check.
 */
public class ExternalCheckService {

  private final ExternalCheckConnector connector;
  private final ConsentRegistry consents;
  private final CheckKeyStore keys;
  private final CheckAuditLog audit;
  private final Clock clock;

  /**
   * @param connector the provider
   * @param consents the consent register
   * @param keys the provider keys
   * @param audit where the audit lines go
   * @param clock the clock (injected, never ambient)
   */
  public ExternalCheckService(
      ExternalCheckConnector connector,
      ConsentRegistry consents,
      CheckKeyStore keys,
      CheckAuditLog audit,
      Clock clock) {
    this.connector = Objects.requireNonNull(connector, "connector must not be null");
    this.consents = Objects.requireNonNull(consents, "consents must not be null");
    this.keys = Objects.requireNonNull(keys, "keys must not be null");
    this.audit = Objects.requireNonNull(audit, "audit must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Searches the court records.
   *
   * @param call the check asked for
   * @return the result
   */
  public CheckResult verifyCourtRecords(CheckCall call) {
    return verify(CheckPurpose.COURT_RECORDS, call, connector::verifyCourtRecords);
  }

  /**
   * Reads a credit report.
   *
   * @param call the check asked for
   * @return the result
   */
  public CheckResult verifyCredit(CheckCall call) {
    return verify(CheckPurpose.CREDIT, call, connector::verifyCredit);
  }

  /**
   * Verifies the identity.
   *
   * @param call the check asked for
   * @return the result
   */
  public CheckResult verifyIdentity(CheckCall call) {
    return verify(CheckPurpose.IDENTITY, call, connector::verifyIdentity);
  }

  private CheckResult verify(
      CheckPurpose purpose, CheckCall call, Function<CheckRequest, CheckResult> operation) {
    String subjectId = call.subject().subjectId();

    if (!consents.isGranted(subjectId, purpose)) {
      return refused(purpose, call, CheckRefusal.NO_CONSENT);
    }
    Optional<CheckKey> key = resolveKey(call.userId());
    if (key.isEmpty()) {
      return refused(purpose, call, CheckRefusal.NO_KEY);
    }

    Instant start = clock.instant();
    CheckResult result = answer(operation, new CheckRequest(call.subject(), key.get()));
    Duration took = Duration.between(start, clock.instant());
    audit.record(
        new CheckAudit(
            clock.instant(),
            call.userId(),
            call.runId(),
            connector.provider(),
            purpose,
            key.get().source(),
            result.outcome(),
            null,
            subjectRef(subjectId),
            took.toMillis(),
            result.providerRef()));
    return result;
  }

  private CheckResult answer(Function<CheckRequest, CheckResult> operation, CheckRequest request) {
    try {
      CheckResult result = operation.apply(request);
      if (result == null || result.outcome() == CheckOutcome.REFUSED) {
        // A connector cannot refuse for the service: that is a provider fault.
        return CheckResult.answered(CheckOutcome.PROVIDER_ERROR, null);
      }
      return result;
    } catch (RuntimeException failure) {
      // The message stays out: it may carry the provider's payload or the subject.
      return CheckResult.answered(CheckOutcome.PROVIDER_ERROR, null);
    }
  }

  private CheckResult refused(CheckPurpose purpose, CheckCall call, CheckRefusal refusal) {
    audit.record(
        new CheckAudit(
            clock.instant(),
            call.userId(),
            call.runId(),
            connector.provider(),
            purpose,
            null,
            CheckOutcome.REFUSED,
            refusal,
            subjectRef(call.subject().subjectId()),
            null,
            null));
    return CheckResult.refused(refusal);
  }

  /** The actor's own key when there is one, else the platform's; empty when neither. */
  private Optional<CheckKey> resolveKey(String userId) {
    String provider = connector.provider();
    if (userId != null) {
      Optional<String> own = keys.userKey(userId, provider);
      if (own.isPresent()) {
        return Optional.of(new CheckKey(KeySource.USER, own.get()));
      }
    }
    return keys.platformKey(provider).map(value -> new CheckKey(KeySource.PLATFORM, value));
  }

  /** The audit names a subject by the hash of its id, never by the id or a name. */
  static String subjectRef(String subjectId) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(subjectId.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException impossible) {
      throw new UnsupportedOperationException("SHA-256 is part of every Java runtime", impossible);
    }
  }
}
