package com.krizaka.orazaka.core.application.checks;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.orazaka.core.domain.model.checks.CheckAudit;
import com.krizaka.orazaka.core.domain.model.checks.CheckCall;
import com.krizaka.orazaka.core.domain.model.checks.CheckOutcome;
import com.krizaka.orazaka.core.domain.model.checks.CheckPurpose;
import com.krizaka.orazaka.core.domain.model.checks.CheckRefusal;
import com.krizaka.orazaka.core.domain.model.checks.CheckRequest;
import com.krizaka.orazaka.core.domain.model.checks.CheckResult;
import com.krizaka.orazaka.core.domain.model.checks.CheckSubject;
import com.krizaka.orazaka.core.domain.model.checks.KeySource;
import com.krizaka.orazaka.core.domain.ports.outbound.CheckAuditLog;
import com.krizaka.orazaka.core.domain.ports.outbound.CheckKeyStore;
import com.krizaka.orazaka.core.domain.ports.outbound.ConsentRegistry;
import com.krizaka.orazaka.core.domain.ports.outbound.ExternalCheckConnector;
import com.krizaka.orazaka.core.infrastructure.checks.MockConnector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExternalCheckServiceTest {

  private static final String SUBJECT = "subject-1";
  private static final String USER = "user-1";
  private static final String PROVIDER = "soquij";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);

  private final Set<String> consents = new java.util.HashSet<>();
  private final Map<String, String> userKeys = new HashMap<>();
  private final Map<String, String> platformKeys = new HashMap<>();
  private final List<CheckAudit> audit = new ArrayList<>();
  private final List<CheckRequest> reached = new ArrayList<>();

  private ConsentRegistry registry;
  private CheckKeyStore keyStore;
  private CheckAuditLog log;

  @BeforeEach
  void setUp() {
    registry = (subjectId, purpose) -> consents.contains(subjectId + "|" + purpose);
    keyStore =
        new CheckKeyStore() {
          @Override
          public java.util.Optional<String> userKey(String userId, String provider) {
            return java.util.Optional.ofNullable(userKeys.get(userId + "|" + provider));
          }

          @Override
          public java.util.Optional<String> platformKey(String provider) {
            return java.util.Optional.ofNullable(platformKeys.get(provider));
          }
        };
    log = audit::add;
    consents.add(SUBJECT + "|" + CheckPurpose.COURT_RECORDS);
    platformKeys.put(PROVIDER, "platform-key");
  }

  /** A connector that records every request it receives and answers what it is told. */
  private ExternalCheckConnector connector(Function<CheckRequest, CheckResult> answer) {
    return new ExternalCheckConnector() {
      @Override
      public String provider() {
        return PROVIDER;
      }

      @Override
      public CheckResult verifyCourtRecords(CheckRequest request) {
        reached.add(request);
        return answer.apply(request);
      }

      @Override
      public CheckResult verifyCredit(CheckRequest request) {
        reached.add(request);
        return answer.apply(request);
      }

      @Override
      public CheckResult verifyIdentity(CheckRequest request) {
        reached.add(request);
        return answer.apply(request);
      }
    };
  }

  private ExternalCheckService service(ExternalCheckConnector connector) {
    return new ExternalCheckService(connector, registry, keyStore, log, CLOCK);
  }

  private static CheckCall call(String userId) {
    return new CheckCall(
        userId,
        UUID.fromString("00000000-0000-0000-0000-000000000042"),
        new CheckSubject(SUBJECT, "Tremblay", "Marie", null));
  }

  @Test
  @DisplayName("without consent the provider is never reached, and the refusal is audited")
  void refusesWithoutConsent() {
    consents.clear();
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(result.outcome()).isEqualTo(CheckOutcome.REFUSED);
    assertThat(result.refusal()).isEqualTo(CheckRefusal.NO_CONSENT);
    assertThat(reached).isEmpty();
    assertThat(audit).hasSize(1);
    assertThat(audit.get(0).outcome()).isEqualTo(CheckOutcome.REFUSED);
    assertThat(audit.get(0).keySource()).isNull();
  }

  @Test
  @DisplayName("consent is per purpose: court records consent does not allow a credit check")
  void consentIsPerPurpose() {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")));

    CheckResult result = service.verifyCredit(call(USER));

    assertThat(result.refusal()).isEqualTo(CheckRefusal.NO_CONSENT);
    assertThat(reached).isEmpty();
  }

  @Test
  @DisplayName("the actor's own key is used over the platform key")
  void userKeyOverPlatformKey() {
    userKeys.put(USER + "|" + PROVIDER, "user-key");
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "case-7")));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(reached).hasSize(1);
    assertThat(reached.get(0).key().source()).isEqualTo(KeySource.USER);
    assertThat(reached.get(0).key().value()).isEqualTo("user-key");
    assertThat(result.outcome()).isEqualTo(CheckOutcome.MATCH);
    assertThat(result.providerRef()).isEqualTo("case-7");
    assertThat(audit.get(0).keySource()).isEqualTo(KeySource.USER);
  }

  @Test
  @DisplayName("without an own key the platform key is used")
  void platformKeyWhenNoUserKey() {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.NO_MATCH, null)));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(reached.get(0).key().source()).isEqualTo(KeySource.PLATFORM);
    assertThat(result.outcome()).isEqualTo(CheckOutcome.NO_MATCH);
    assertThat(audit.get(0).keySource()).isEqualTo(KeySource.PLATFORM);
  }

  @Test
  @DisplayName("with neither key the call is refused before the provider")
  void refusesWithoutAnyKey() {
    platformKeys.clear();
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(result.refusal()).isEqualTo(CheckRefusal.NO_KEY);
    assertThat(reached).isEmpty();
    assertThat(audit).hasSize(1);
  }

  @Test
  @DisplayName("a connector that throws is a provider error, and its message is not audited")
  void providerExceptionIsAnOutcomeWithoutItsMessage() {
    ExternalCheckService service =
        service(
            connector(
                r -> {
                  throw new IllegalStateException("payload: Tremblay Marie 1980");
                }));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(result.outcome()).isEqualTo(CheckOutcome.PROVIDER_ERROR);
    assertThat(audit).hasSize(1);
    assertThat(audit.get(0).outcome()).isEqualTo(CheckOutcome.PROVIDER_ERROR);
    assertThat(audit.get(0).toString()).doesNotContain("Tremblay", "1980");
  }

  @Test
  @DisplayName("a connector that answers REFUSED is treated as a provider fault")
  void aConnectorCannotRefuseForTheService() {
    ExternalCheckService service =
        service(
            connector(
                r -> {
                  return null;
                }));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(result.outcome()).isEqualTo(CheckOutcome.PROVIDER_ERROR);
    assertThat(result.refusal()).isNull();
  }

  @Test
  @DisplayName("a timeout is answered as TIMEOUT and audited with its duration")
  void timeoutIsAudited() {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.TIMEOUT, null)));

    CheckResult result = service.verifyCourtRecords(call(USER));

    assertThat(result.outcome()).isEqualTo(CheckOutcome.TIMEOUT);
    assertThat(audit.get(0).outcome()).isEqualTo(CheckOutcome.TIMEOUT);
    assertThat(audit.get(0).durationMs()).isNotNull();
  }

  @Test
  @DisplayName("the audit names the subject by a hash, never by its id, and carries the run")
  void auditHashesTheSubject() {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.NO_MATCH, null)));

    service.verifyCourtRecords(call(USER));

    CheckAudit line = audit.get(0);
    assertThat(line.subjectRef()).hasSize(64).doesNotContain(SUBJECT);
    assertThat(line.runId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000042"));
    assertThat(line.at()).isEqualTo(Instant.parse("2026-10-23T12:00:00Z"));
    assertThat(line.provider()).isEqualTo(PROVIDER);
  }

  @Test
  @DisplayName("the recorded mock answers the service end to end, without network")
  void mockAnswersTheService() {
    consents.add(SUBJECT + "|" + CheckPurpose.CREDIT);
    platformKeys.put("mock", "mock-key");
    ExternalCheckService service = service(new MockConnector());

    CheckResult matched =
        service.verifyCredit(
            new CheckCall(USER, null, new CheckSubject(SUBJECT, "Tremblay", "Marie", null)));
    CheckResult unknown =
        service.verifyCredit(
            new CheckCall(USER, null, new CheckSubject(SUBJECT, "Inconnu", "Marie", null)));

    assertThat(matched.outcome()).isEqualTo(CheckOutcome.MATCH);
    assertThat(unknown.outcome()).isEqualTo(CheckOutcome.NO_MATCH);
    assertThat(audit).hasSize(2);
  }

  @Test
  @DisplayName("consent withdrawn stops the next call at once")
  void withdrawnConsentStopsTheNextCall() {
    ExternalCheckService service =
        service(connector(r -> CheckResult.answered(CheckOutcome.MATCH, "x")));
    assertThat(service.verifyCourtRecords(call(USER)).outcome()).isEqualTo(CheckOutcome.MATCH);

    consents.clear();

    assertThat(service.verifyCourtRecords(call(USER)).refusal()).isEqualTo(CheckRefusal.NO_CONSENT);
    assertThat(reached).hasSize(1);
    assertThat(audit).hasSize(2);
  }
}
