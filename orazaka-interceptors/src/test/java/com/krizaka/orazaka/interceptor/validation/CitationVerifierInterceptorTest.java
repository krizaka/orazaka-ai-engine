package com.krizaka.orazaka.interceptor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.orazaka.core.domain.model.PromptContext;
import com.krizaka.orazaka.core.domain.model.research.Claim;
import com.krizaka.orazaka.core.domain.model.research.ClaimStatus;
import com.krizaka.orazaka.core.domain.model.research.GroundedAnswer;
import com.krizaka.orazaka.core.domain.model.research.ReadDocument;
import com.krizaka.orazaka.core.domain.model.research.VerifyMode;
import com.krizaka.orazaka.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CitationVerifierInterceptorTest {

  private final List<VerifyMode> modes = new ArrayList<>();
  private final List<List<ReadDocument>> reads = new ArrayList<>();
  private final List<GroundedAnswer> seen = new ArrayList<>();

  private CitationVerifierInterceptor interceptor(String mode) {
    CitationVerifier verifier =
        (answer, m, read) -> {
          modes.add(m);
          reads.add(read);
          seen.add(answer);
          return new GroundedAnswer(
              answer.answer(),
              answer.claims().stream()
                  .map(
                      c ->
                          new Claim(
                              c.text(),
                              ClaimStatus.TO_VALIDATE,
                              com.krizaka.orazaka.core.domain.model.research.ClaimReason.NO_SOURCE,
                              c.citations()))
                  .toList(),
              answer.sources());
        };
    RuntimeConfigProvider config = mock(RuntimeConfigProvider.class);
    when(config.getString("research.verify.mode", "MARK")).thenReturn(mode);
    return new CitationVerifierInterceptor(verifier, config);
  }

  @Test
  @DisplayName("an answer that is not in the citation shape passes through untouched")
  void otherAnswersPassThrough() {
    CitationVerifierInterceptor stage = interceptor("MARK");
    PromptContext context = mock(PromptContext.class);
    assertThat(stage.screenResponse(context, "Bonjour !")).isEqualTo("Bonjour !");
    assertThat(stage.screenResponse(context, "{\"answer\":\"x\"}")).isEqualTo("{\"answer\":\"x\"}");
    assertThat(stage.screenResponse(context, "{not json")).isEqualTo("{not json");
    assertThat(seen).isEmpty();
  }

  @Test
  @DisplayName(
      "a citation-shaped answer is verified against what this turn read; the model's status is not"
          + " passed on; the mode is read")
  void citationShapeIsVerified() {
    String model =
        """
        {"answer":"Trente jours.","claims":[{"text":"Trente jours.","status":"VERIFIED",
         "citations":[{"url":"https://example.org/r","quote":"trente jours","contentHash":"%s","fetchedAt":"t"}]}],
         "sources":[{"url":"https://example.org/r","title":"R","sourceKey":"site"}]}
        """
            .formatted("a".repeat(64));
    ReadDocument read = new ReadDocument("site", "https://example.org/r", "a".repeat(64));
    PromptContext context =
        new PromptContext("q", java.util.Map.of()).withResearchRead(List.of(read));
    String served = interceptor("STRICT").screenResponse(context, model);
    assertThat(modes).containsExactly(VerifyMode.STRICT);
    assertThat(reads).containsExactly(List.of(read));
    assertThat(seen.getFirst().claims().getFirst().status()).isEqualTo(ClaimStatus.TO_VALIDATE);
    assertThat(served)
        .contains(
            "\"status\":\"TO_VALIDATE\"", "\"reason\":\"NO_SOURCE\"", "\"sourceKey\":\"site\"");
  }
}
