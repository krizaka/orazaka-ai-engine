package com.krizaka.orazaka.core.application.checks;

import com.krizaka.orazaka.core.domain.model.checks.*;
import com.krizaka.orazaka.core.domain.ports.inbound.ExternalCheckCapability;
import com.krizaka.orazaka.core.domain.ports.outbound.CheckAuditLog;
import com.krizaka.orazaka.core.domain.ports.outbound.CheckKeyStore;
import com.krizaka.orazaka.core.domain.ports.outbound.CheckMeter;
import com.krizaka.orazaka.core.domain.ports.outbound.CheckSubjectPepper;
import com.krizaka.orazaka.core.domain.ports.outbound.ConsentRegistry;
import com.krizaka.orazaka.core.domain.ports.outbound.ExternalCheckConnector;
import com.krizaka.orazaka.core.infrastructure.support.CoreException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The one door to the external checks (orazaka-ai-engine#73). Every call, in this order:
 *
 * <ol>
 *   <li>the subject reference, a keyed HMAC-SHA-256 of the subject id: no sealed pepper, no check;
 *   <li>consent for the purpose: none, or withdrawn, refuses without touching the provider;
 *   <li>the key: the actor's own, then the platform's; neither refuses;
 *   <li>one {@code CALL} held against the run budget: a budget that cannot hold it refuses;
 *   <li>the connector, which may answer an outcome or fail: a failure is a {@code PROVIDER_ERROR},
 *       and its message is neither returned nor audited;
 *   <li>one audit line, whatever happened;
 *   <li>the hold settled when the provider answered, released (nothing charged) otherwise.
 * </ol>
 *
 * <p>The connector is never reached without consent, a key and a hold, so no route can skip any of
 * them. The same door is the engine capability a blueprint step calls (orazaka-ai-engine#84).
 */
public class ExternalCheckService implements ExternalCheckCapability {

  /** A pepper shorter than the HMAC's block of security is refused. */
  private static final int MIN_PEPPER_BYTES = 32;

  private static final String HMAC = "HmacSHA256";

  private final ExternalCheckConnector connector;
  private final ConsentRegistry consents;
  private final CheckKeyStore keys;
  private final CheckAuditLog audit;
  private final CheckMeter meter;
  private final CheckSubjectPepper pepper;
  private final Clock clock;

  /**
   * @param connector the provider
   * @param consents the consent register
   * @param keys the provider keys
   * @param audit where the audit lines go
   * @param meter the {@code CALL} metering against the run budget
   * @param pepper the sealed pepper of the subject reference
   * @param clock the clock (injected, never ambient)
   */
  public ExternalCheckService(
      ExternalCheckConnector connector,
      ConsentRegistry consents,
      CheckKeyStore keys,
      CheckAuditLog audit,
      CheckMeter meter,
      CheckSubjectPepper pepper,
      Clock clock) {
    this.connector = Objects.requireNonNull(connector, "connector must not be null");
    this.consents = Objects.requireNonNull(consents, "consents must not be null");
    this.keys = Objects.requireNonNull(keys, "keys must not be null");
    this.audit = Objects.requireNonNull(audit, "audit must not be null");
    this.meter = Objects.requireNonNull(meter, "meter must not be null");
    this.pepper = Objects.requireNonNull(pepper, "pepper must not be null");
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

  @Override
  public CheckStepResult run(String featureKey, CheckCall call) {
    CheckPurpose purpose =
        CheckPurpose.ofFeatureKey(featureKey)
            .orElseThrow(
                () -> new IllegalArgumentException("not an external check: " + featureKey));
    CheckResult result;
    if (purpose == CheckPurpose.COURT_RECORDS) {
      result = verifyCourtRecords(call);
    } else if (purpose == CheckPurpose.CREDIT) {
      result = verifyCredit(call);
    } else {
      result = verifyIdentity(call);
    }
    return CheckStepResult.of(result);
  }

  private CheckResult verify(
      CheckPurpose purpose, CheckCall call, Function<CheckRequest, CheckResult> operation) {
    String subjectId = call.subject().subjectId();
    String subjectRef = subjectRef(subjectId);

    if (!consents.isGranted(subjectId, purpose)) {
      return refused(purpose, call, subjectRef, CheckRefusal.NO_CONSENT);
    }
    Optional<CheckKey> key = resolveKey(call.userId());
    if (key.isEmpty()) {
      return refused(purpose, call, subjectRef, CheckRefusal.NO_KEY);
    }
    CheckMeter.Hold hold;
    try {
      hold = meter.hold(call.runId(), call.userId(), connector.provider(), purpose);
    } catch (CheckMeter.BudgetExhaustedException exhausted) {
      return refused(purpose, call, subjectRef, CheckRefusal.BUDGET_EXHAUSTED);
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
            subjectRef,
            took.toMillis(),
            result.providerRef()));
    if (result.isAnswered()) {
      hold.settle();
    } else {
      hold.release("the provider did not answer: " + result.outcome());
    }
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

  private CheckResult refused(
      CheckPurpose purpose, CheckCall call, String subjectRef, CheckRefusal refusal) {
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
            subjectRef,
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

  /**
   * The audit names a subject by a keyed HMAC-SHA-256 of its id, never by the id, a name or a plain
   * hash (which an id list could enumerate). No sealed pepper, or a short one, stops the call.
   */
  private String subjectRef(String subjectId) {
    byte[] key =
        pepper
            .pepper()
            .filter(bytes -> bytes.length >= MIN_PEPPER_BYTES)
            .orElseThrow(
                () ->
                    new CoreException(
                        "no subject pepper of at least "
                            + MIN_PEPPER_BYTES
                            + " bytes is sealed: external checks stay off"));
    try {
      Mac mac = Mac.getInstance(HMAC);
      mac.init(new SecretKeySpec(key, HMAC));
      return HexFormat.of().formatHex(mac.doFinal(subjectId.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException impossible) {
      throw new UnsupportedOperationException(
          "HmacSHA256 is part of every Java runtime", impossible);
    }
  }
}
