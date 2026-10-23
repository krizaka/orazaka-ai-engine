package com.krizaka.orazaka.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.orazaka.core.domain.model.research.Citation;
import com.krizaka.orazaka.core.domain.model.research.Claim;
import com.krizaka.orazaka.core.domain.model.research.ClaimReason;
import com.krizaka.orazaka.core.domain.model.research.ClaimStatus;
import com.krizaka.orazaka.core.domain.model.research.GroundedAnswer;
import com.krizaka.orazaka.core.domain.model.research.VerifyMode;
import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.orazaka.tools.domain.model.research.FetchAudit;
import com.krizaka.orazaka.tools.domain.model.research.FetchedDocument;
import com.krizaka.orazaka.tools.domain.model.research.ResearchSource;
import com.krizaka.orazaka.tools.domain.port.ResearchStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The deterministic verifier on recorded documents, in French and English (ADR-087 §3). The two
 * documents are test texts written for this suite, with no legal force.
 */
class CitationVerificationServiceTest {

  private static final String FR_URL = "https://example.org/reglement-fictif";
  private static final String EN_URL = "https://example.org/test-regulation";
  private static final String FR_HASH = "f".repeat(64);
  private static final String EN_HASH = "e".repeat(64);

  private final Map<String, FetchedDocument> documents = new HashMap<>();
  private CitationVerificationService verifier;

  private static String golden(String name) {
    try (InputStream in =
        CitationVerificationServiceTest.class.getResourceAsStream("/research/golden/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private void record(String url, String hash, String text) {
    Instant at = Instant.parse("2026-10-23T12:00:00Z");
    documents.put(
        url + "#" + hash,
        new FetchedDocument(
            url, hash, at, 200, "text/plain", text.length(), "", text, at.plusSeconds(60)));
  }

  @BeforeEach
  void setUp() {
    record(FR_URL, FR_HASH, golden("fr-reglement-fictif.txt"));
    record(EN_URL, EN_HASH, golden("en-test-regulation.txt"));
    ResearchStore store =
        new ResearchStore() {
          @Override
          public Optional<ResearchSource> source(String sourceKey) {
            return Optional.empty();
          }

          @Override
          public Optional<FetchedDocument> freshDocument(String url, Instant now) {
            return Optional.empty();
          }

          @Override
          public Optional<FetchedDocument> document(String url, String contentHash) {
            return Optional.ofNullable(documents.get(url + "#" + contentHash));
          }

          @Override
          public void saveDocument(FetchedDocument document, String requestedUrl, UUID sourceId) {}

          @Override
          public void audit(FetchAudit audit) {}
        };
    RuntimeConfigProvider config =
        new RuntimeConfigProvider() {
          @Override
          public boolean getBoolean(String key, boolean defaultValue) {
            return defaultValue;
          }

          @Override
          public int getInt(String key, int defaultValue) {
            return "research.quote.max-chars".equals(key) ? 120 : defaultValue;
          }

          @Override
          public String getString(String key, String defaultValue) {
            return defaultValue;
          }
        };
    verifier = new CitationVerificationService(store, config);
  }

  private static Claim claim(String text, String url, String hash, String quote) {
    return new Claim(
        text, null, null, List.of(new Citation(url, quote, hash, "2026-10-23T12:00:00Z")));
  }

  private GroundedAnswer verify(VerifyMode mode, String prose, Claim... claims) {
    return verifier.verify(new GroundedAnswer(prose, List.of(claims), List.of()), mode);
  }

  @Test
  @DisplayName("FR golden: plain typing finds the passage despite « », ’, U+202F, U+00A0, œ")
  void frenchQuoteIsFound() {
    GroundedAnswer out =
        verify(
            VerifyMode.MARK,
            "",
            claim(
                "L'article 12 interdit une somme autre que le loyer.",
                FR_URL,
                FR_HASH,
                "ne peut exiger \"une somme d'argent\" autre que le loyer ; il répond de l'oeuvre : c'est la règle"));
    assertThat(out.claims())
        .singleElement()
        .extracting(Claim::status)
        .isEqualTo(ClaimStatus.VERIFIED);
  }

  @Test
  @DisplayName("FR golden: an NFD quote is the same passage")
  void frenchNfdQuoteIsFound() {
    String nfd =
        Normalizer.normalize("Le délai de résiliation est de trente jours", Normalizer.Form.NFD);
    GroundedAnswer out = verify(VerifyMode.MARK, "", claim("Trente jours.", FR_URL, FR_HASH, nfd));
    assertThat(out.claims().getFirst().status()).isEqualTo(ClaimStatus.VERIFIED);
  }

  @Test
  @DisplayName("FR golden: one word changed is not the passage")
  void frenchChangedWordIsNotFound() {
    GroundedAnswer out =
        verify(
            VerifyMode.MARK,
            "",
            claim(
                "Quatorze jours.",
                FR_URL,
                FR_HASH,
                "Le délai de résiliation est de quatorze jours"));
    assertThat(out.claims().getFirst().status()).isEqualTo(ClaimStatus.TO_VALIDATE);
    assertThat(out.claims().getFirst().reason()).isEqualTo(ClaimReason.QUOTE_NOT_FOUND);
  }

  @Test
  @DisplayName(
      "EN golden: curly quotes, the em dash and the ellipsis match; a changed word does not")
  void englishGolden() {
    GroundedAnswer out =
        verify(
            VerifyMode.MARK,
            "",
            claim(
                "Only the first payment is due.",
                EN_URL,
                EN_HASH,
                "The tenant's \"first payment\" - and only that... is due at signing"),
            claim(
                "Ninety days.", EN_URL, EN_HASH, "takes effect ninety days after it is received"));
    assertThat(out.claims())
        .extracting(Claim::status)
        .containsExactly(ClaimStatus.VERIFIED, ClaimStatus.TO_VALIDATE);
  }

  @Test
  @DisplayName("no citation, an unknown document, a quote too long: never verified")
  void notVerified() {
    GroundedAnswer out =
        verify(
            VerifyMode.MARK,
            "",
            new Claim("Said from memory.", null, null, List.of()),
            claim("Unknown document.", EN_URL, "0".repeat(64), "first payment"),
            claim("Too long.", EN_URL, EN_HASH, "x".repeat(121)));
    assertThat(out.claims())
        .extracting(Claim::reason)
        .containsExactly(
            ClaimReason.NO_SOURCE, ClaimReason.URL_UNRESOLVED, ClaimReason.QUOTE_NOT_FOUND);
  }

  @Test
  @DisplayName("invented references in prose become TO_VALIDATE lines; cited ones do not")
  void unlistedReferences() {
    String prose =
        "See https://www.legisquebec.gouv.qc.ca/invented and the repository krizaka/legal-rules;"
            + " article 12 applies, and art. 99 too.";
    GroundedAnswer out =
        verify(
            VerifyMode.MARK,
            prose,
            claim(
                "L'article 12 interdit une somme autre que le loyer.",
                FR_URL,
                FR_HASH,
                "une somme d’argent"));
    assertThat(out.claims())
        .filteredOn(c -> c.reason() == ClaimReason.UNLISTED_REFERENCE)
        .extracting(Claim::text)
        .contains("https://www.legisquebec.gouv.qc.ca/invented", "krizaka/legal-rules", "art. 99")
        .doesNotContain("article 12");
  }

  @Test
  @DisplayName("STRICT drops what is not verified, and the prose keeps only verified claims")
  void strictDrops() {
    GroundedAnswer out =
        verify(
            VerifyMode.STRICT,
            "Trente jours, et quatorze ailleurs.",
            claim("Trente jours.", FR_URL, FR_HASH, "trente jours, sauf entente écrite"),
            claim("Quatorze jours.", FR_URL, FR_HASH, "quatorze jours"));
    assertThat(out.claims()).extracting(Claim::text).containsExactly("Trente jours.");
    assertThat(out.answer()).isEqualTo("Trente jours.");
  }

  @Test
  @DisplayName("the model's own status is never trusted")
  void statusComesFromTheVerifier() {
    Claim lying =
        new Claim(
            "Quatorze jours.",
            ClaimStatus.VERIFIED,
            null,
            List.of(new Citation(FR_URL, "quatorze jours", FR_HASH, "")));
    assertThat(verify(VerifyMode.MARK, "", lying).claims().getFirst().status())
        .isEqualTo(ClaimStatus.TO_VALIDATE);
  }
}
