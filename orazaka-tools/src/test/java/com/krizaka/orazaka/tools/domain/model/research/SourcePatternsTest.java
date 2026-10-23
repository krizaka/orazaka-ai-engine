package com.krizaka.orazaka.tools.domain.model.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SourcePatternsTest {

  private static ResearchSource source(List<String> allow, List<String> deny) {
    return new ResearchSource(
        UUID.randomUUID(), "s", "FETCH", "http", allow, deny, false, 60, 10, true);
  }

  @Test
  @DisplayName("an empty allow list allows nothing")
  void closedByDefault() {
    assertThat(SourcePatterns.allows(source(List.of(), List.of()), "example.org", "/")).isFalse();
  }

  @Test
  @DisplayName("** spans segments, * stays in one, the host matches without case")
  void globs() {
    ResearchSource s =
        source(List.of("Laws-Lois.Justice.gc.ca/eng/acts/*", "docs.example.org/**"), List.of());
    assertThat(SourcePatterns.allows(s, "laws-lois.justice.gc.ca", "/eng/acts/C-46")).isTrue();
    assertThat(SourcePatterns.allows(s, "laws-lois.justice.gc.ca", "/eng/acts/C-46/page-1"))
        .isFalse();
    assertThat(SourcePatterns.allows(s, "docs.example.org", "/a/b/c")).isTrue();
    assertThat(SourcePatterns.allows(s, "evil-docs.example.org", "/a")).isFalse();
  }

  @Test
  @DisplayName("*.host allows subdomains, not the host's look-alikes")
  void subdomains() {
    ResearchSource s = source(List.of("*.gc.ca/**"), List.of());
    assertThat(SourcePatterns.allows(s, "laws-lois.justice.gc.ca", "/x")).isTrue();
    assertThat(SourcePatterns.allows(s, "gc.ca", "/x")).isFalse();
    assertThat(SourcePatterns.allows(s, "evilgc.ca", "/x")).isFalse();
  }

  @Test
  @DisplayName("deny wins over allow")
  void denyWins() {
    ResearchSource s = source(List.of("example.org/**"), List.of("example.org/private/**"));
    assertThat(SourcePatterns.allows(s, "example.org", "/public/a")).isTrue();
    assertThat(SourcePatterns.allows(s, "example.org", "/private/a")).isFalse();
  }
}
