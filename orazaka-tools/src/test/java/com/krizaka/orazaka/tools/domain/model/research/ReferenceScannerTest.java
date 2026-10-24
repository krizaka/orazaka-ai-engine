package com.krizaka.orazaka.tools.domain.model.research;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReferenceScannerTest {

  @Test
  @DisplayName(
      "URLs, owner/repo names and statute references are found; dates are not repositories")
  void references() {
    assertThat(
            ReferenceScanner.references(
                "Voir https://example.org/a. Le dépôt krizaka/orazaka-packs, l'art. 1904 C.c.Q., la RLRQ c. P-40.1"
                    + " et le 10/23 ; also R.S.C. 1985."))
        .contains(
            "https://example.org/a",
            "krizaka/orazaka-packs",
            "art. 1904",
            "RLRQ c. P-40.1",
            "R.S.C. 1985")
        .doesNotContain("10/23");
  }

  @Test
  @DisplayName("a date at the end of a sentence is not a repository: « Due on 10/23. »")
  void aSentenceEndingDateIsNotARepository() {
    assertThat(ReferenceScanner.references("Due on 10/23. Paid 3/4.")).isEmpty();
    assertThat(ReferenceScanner.references("Due on 10/23, krizaka/rules."))
        .containsExactly("krizaka/rules");
  }

  @Test
  @DisplayName("references compare by identity: art. = article, the whole provision, URLs exactly")
  void keys() {
    assertThat(ReferenceScanner.key("article 1931")).isEqualTo(ReferenceScanner.key("art. 1931"));
    assertThat(ReferenceScanner.key("Art. 1931")).isEqualTo(ReferenceScanner.key("articles 1931"));
    assertThat(ReferenceScanner.key("art. 1")).isNotEqualTo(ReferenceScanner.key("art. 12"));
    assertThat(ReferenceScanner.key("RLRQ c. P-40.1"))
        .isEqualTo(ReferenceScanner.key("RLRQ, c. P-40.1"));
    assertThat(ReferenceScanner.key("https://a.org/R"))
        .isNotEqualTo(ReferenceScanner.key("https://a.org/r"));
    assertThat(ReferenceScanner.key("Krizaka/Rules"))
        .isEqualTo(ReferenceScanner.key("krizaka/rules"));
  }
}
