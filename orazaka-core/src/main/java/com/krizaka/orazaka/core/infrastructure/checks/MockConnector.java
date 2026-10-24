package com.krizaka.orazaka.core.infrastructure.checks;

import com.krizaka.orazaka.core.domain.model.checks.CheckOutcome;
import com.krizaka.orazaka.core.domain.model.checks.CheckRequest;
import com.krizaka.orazaka.core.domain.model.checks.CheckResult;
import com.krizaka.orazaka.core.domain.ports.outbound.ExternalCheckConnector;
import java.util.Map;
import java.util.Objects;

/**
 * A connector that answers from recorded fixtures, with no network: the demo's and the CI's
 * connector (orazaka-ai-engine#73). The fixture is chosen by the subject's last name, so a test
 * reads as a sentence: the subject {@code Tremblay} matches.
 *
 * <p>An unknown last name answers {@code NO_MATCH}.
 */
public final class MockConnector implements ExternalCheckConnector {

  /** The provider name of the mock. */
  private static final String PROVIDER = "mock";

  /** The recorded answers of the demo: one per outcome, and one unknown name. */
  private static final Map<String, CheckOutcome> RECORDED =
      Map.of(
          "Tremblay", CheckOutcome.MATCH,
          "Gagnon", CheckOutcome.NO_MATCH,
          "Roy", CheckOutcome.PROVIDER_ERROR,
          "Bouchard", CheckOutcome.TIMEOUT);

  private final Map<String, CheckOutcome> answers;

  /** The connector over the recorded demo answers. */
  public MockConnector() {
    this(RECORDED);
  }

  /**
   * The connector over the given answers.
   *
   * @param answers the outcome for each last name
   */
  public MockConnector(Map<String, CheckOutcome> answers) {
    this.answers = Map.copyOf(Objects.requireNonNull(answers, "answers must not be null"));
  }

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public CheckResult verifyCourtRecords(CheckRequest request) {
    return answer(request);
  }

  @Override
  public CheckResult verifyCredit(CheckRequest request) {
    return answer(request);
  }

  @Override
  public CheckResult verifyIdentity(CheckRequest request) {
    return answer(request);
  }

  private CheckResult answer(CheckRequest request) {
    CheckOutcome outcome =
        answers.getOrDefault(request.subject().lastName(), CheckOutcome.NO_MATCH);
    String ref = outcome == CheckOutcome.MATCH ? "mock-" + request.subject().lastName() : null;
    return CheckResult.answered(outcome, ref);
  }
}
