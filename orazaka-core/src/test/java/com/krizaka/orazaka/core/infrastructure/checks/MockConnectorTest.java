package com.krizaka.orazaka.core.infrastructure.checks;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.orazaka.core.domain.model.checks.CheckKey;
import com.krizaka.orazaka.core.domain.model.checks.CheckOutcome;
import com.krizaka.orazaka.core.domain.model.checks.CheckRequest;
import com.krizaka.orazaka.core.domain.model.checks.CheckSubject;
import com.krizaka.orazaka.core.domain.model.checks.KeySource;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockConnectorTest {

  private static CheckRequest request(String lastName) {
    return new CheckRequest(
        new CheckSubject("subject-1", lastName, "Marie", null),
        new CheckKey(KeySource.PLATFORM, "k"));
  }

  @Test
  @DisplayName("the recorded fixtures answer a match, a no match, a provider error and a timeout")
  void recordedFixturesCoverEveryOutcome() {
    MockConnector mock = new MockConnector();

    assertThat(mock.verifyCourtRecords(request("Tremblay")).outcome())
        .isEqualTo(CheckOutcome.MATCH);
    assertThat(mock.verifyIdentity(request("Gagnon")).outcome()).isEqualTo(CheckOutcome.NO_MATCH);
    assertThat(mock.verifyCredit(request("Roy")).outcome()).isEqualTo(CheckOutcome.PROVIDER_ERROR);
    assertThat(mock.verifyCredit(request("Bouchard")).outcome()).isEqualTo(CheckOutcome.TIMEOUT);
  }

  @Test
  @DisplayName("a match carries the mock's case reference, a no match none")
  void matchCarriesAReference() {
    MockConnector mock = new MockConnector();

    assertThat(mock.verifyCourtRecords(request("Tremblay")).providerRef())
        .isEqualTo("mock-Tremblay");
    assertThat(mock.verifyCourtRecords(request("Gagnon")).providerRef()).isNull();
  }

  @Test
  @DisplayName("an unknown name is no match, never a match")
  void unknownNameIsNoMatch() {
    assertThat(new MockConnector().verifyCourtRecords(request("Inconnu")).outcome())
        .isEqualTo(CheckOutcome.NO_MATCH);
  }

  @Test
  @DisplayName("the fixtures given to the connector are the ones it answers")
  void givenFixturesAreUsed() {
    MockConnector mock = new MockConnector(Map.of("Roy", CheckOutcome.MATCH));

    assertThat(mock.verifyCourtRecords(request("Roy")).outcome()).isEqualTo(CheckOutcome.MATCH);
    assertThat(mock.provider()).isEqualTo("mock");
  }
}
