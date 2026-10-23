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
}
