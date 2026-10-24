package com.krizaka.orazaka.tools.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.orazaka.tools.application.port.law.LawChangeRequests;
import com.krizaka.orazaka.tools.application.port.law.LawCorpusStore;
import com.krizaka.orazaka.tools.application.port.law.LawSource;
import com.krizaka.orazaka.tools.domain.model.law.LawArticle;
import com.krizaka.orazaka.tools.domain.model.law.LawArticleVersion;
import com.krizaka.orazaka.tools.domain.model.law.LawChange;
import com.krizaka.orazaka.tools.domain.model.law.LawTarget;
import com.krizaka.orazaka.tools.domain.model.law.LawWatchReport;
import com.krizaka.orazaka.tools.infrastructure.adapter.law.LawStatuteXmlParser;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The watcher on recorded XML: the first real Données Québec slice of C.c.Q. (1851-2000), then
 * derived variants of it (one article amended, one removed). No network.
 */
class LawWatchServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-23T06:00:00Z"), ZoneOffset.UTC);
  private static final LawTarget CCQ = LawTarget.of("QC", "CCQ-1991", List.of("1851-2000"));
  private static final String RECORDED = "/law/CCQ-1991_FR-slice.xml";
  private static final String AMENDED = "/law/CCQ-1991_FR-slice-amended.xml";
  private static final String REMOVED = "/law/CCQ-1991_FR-slice-1942-removed.xml";

  private InMemoryCorpus store;
  private RecordingRequests requests;
  private FixtureSource source;

  @BeforeEach
  void setUp() {
    store = new InMemoryCorpus();
    requests = new RecordingRequests();
    source = new FixtureSource();
  }

  private LawWatchService watcher() {
    return new LawWatchService(List.of(source), List.of(CCQ), store, requests, CLOCK);
  }

  @Test
  void the_first_import_stores_the_texts_and_opens_no_request() {
    source.serve(RECORDED);

    LawWatchReport report = watcher().run();

    assertThat(requests.opened).isEmpty();
    assertThat(report.stored()).isEqualTo(6);
    assertThat(store.currentVersions("CCQ-1991")).hasSize(6);
  }

  @Test
  void an_unchanged_article_opens_nothing() {
    source.serve(RECORDED);
    watcher().run();

    LawWatchReport second = watcher().run();

    assertThat(requests.opened).isEmpty();
    assertThat(second.unchanged()).isEqualTo(6);
    assertThat(second.failures()).isEmpty();
  }

  @Test
  void a_changed_article_opens_one_pull_request_and_becomes_the_current_version() {
    source.serve(RECORDED);
    watcher().run();
    source.serve(AMENDED);

    LawWatchReport report = watcher().run();

    assertThat(requests.opened).hasSize(1);
    LawChange change = requests.opened.getFirst();
    assertThat(change.kind()).isEqualTo(LawChange.Kind.CHANGED);
    assertThat(change.subject().ref()).isEqualTo("1942");
    assertThat(change.previous().text()).contains("trois mois");
    assertThat(change.current().text()).contains("quatre mois");
    assertThat(report.requests()).hasSize(1);

    LawArticle current =
        store.currentVersions("CCQ-1991").stream()
            .map(LawArticleVersion::article)
            .filter(a -> "1942".equals(a.ref()))
            .findFirst()
            .orElseThrow();
    assertThat(current.text()).contains("quatre mois");

    assertThat(watcher().run().requests()).isEmpty();
  }

  @Test
  void a_removed_article_is_flagged_and_closed_as_of_the_run() {
    source.serve(RECORDED);
    watcher().run();
    source.serve(REMOVED);

    watcher().run();

    assertThat(requests.opened).hasSize(1);
    LawChange change = requests.opened.getFirst();
    assertThat(change.kind()).isEqualTo(LawChange.Kind.REMOVED);
    assertThat(change.current()).isNull();
    assertThat(change.subject().ref()).isEqualTo("1942");
    assertThat(change.detectedOn()).isEqualTo(LocalDate.of(2026, 10, 23));
    assertThat(store.currentVersions("CCQ-1991")).noneMatch(v -> "1942".equals(v.article().ref()));
    assertThat(store.closed).containsKey("CCQ-1991|1942|fr");
  }

  @Test
  void a_failed_request_leaves_the_corpus_unchanged_so_the_next_run_detects_it_again() {
    source.serve(RECORDED);
    watcher().run();
    source.serve(AMENDED);
    requests.failNext = true;

    LawWatchReport report = watcher().run();

    assertThat(report.requests()).isEmpty();
    assertThat(report.failures()).hasSize(1);
    assertThat(
            store.currentVersions("CCQ-1991").stream()
                .map(LawArticleVersion::article)
                .filter(a -> "1942".equals(a.ref()))
                .findFirst()
                .orElseThrow()
                .text())
        .contains("trois mois");

    assertThat(watcher().run().requests()).hasSize(1);
  }

  /** A recorded document, parsed by the real parser; the test chooses which one a run reads. */
  private static final class FixtureSource implements LawSource {
    private final LawStatuteXmlParser parser = new LawStatuteXmlParser();
    private String resource;

    void serve(String resource) {
      this.resource = resource;
    }

    @Override
    public boolean serves(String jurisdiction) {
      return "QC".equals(jurisdiction);
    }

    @Override
    public List<LawArticle> fetch(String jurisdiction, Collection<LawTarget> targets) {
      try (InputStream in = LawWatchServiceTest.class.getResourceAsStream(resource)) {
        return parser.parseQuebec(
            Objects.requireNonNull(in, resource), "CCQ-1991", "fr", "https://donnees.test/ccq.xml");
      } catch (java.io.IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  /** The corpus in memory, with the same semantics as the store port. */
  private static final class InMemoryCorpus implements LawCorpusStore {
    private final List<LawArticleVersion> versions = new ArrayList<>();
    private final java.util.Map<String, LocalDate> closed = new java.util.HashMap<>();

    @Override
    public List<LawArticleVersion> currentVersions(String instrument) {
      return versions.stream()
          .filter(v -> v.inForceTo() == null)
          .filter(v -> v.article().instrument().equals(instrument))
          .toList();
    }

    @Override
    public void add(LawArticleVersion version) {
      versions.add(version);
    }

    @Override
    public void close(String instrument, String ref, String language, LocalDate inForceTo) {
      closed.put(instrument + "|" + ref + "|" + language, inForceTo);
      for (int i = 0; i < versions.size(); i++) {
        LawArticleVersion v = versions.get(i);
        if (v.inForceTo() == null && matches(v.article(), instrument, ref, language)) {
          versions.set(
              i, new LawArticleVersion(v.article(), v.firstSeenAt(), v.lastSeenAt(), inForceTo));
        }
      }
    }

    @Override
    public void touch(String instrument, String ref, String language, Instant seenAt) {
      for (int i = 0; i < versions.size(); i++) {
        LawArticleVersion v = versions.get(i);
        if (v.inForceTo() == null && matches(v.article(), instrument, ref, language)) {
          versions.set(i, v.seenAt(seenAt));
        }
      }
    }

    private static boolean matches(LawArticle a, String instrument, String ref, String lang) {
      return a.instrument().equals(instrument) && a.ref().equals(ref) && a.language().equals(lang);
    }
  }

  /** Records the requests; can be told to fail the next one. */
  private static final class RecordingRequests implements LawChangeRequests {
    private final List<LawChange> opened = new ArrayList<>();
    private boolean failNext;

    @Override
    public String open(LawChange change) {
      if (failNext) {
        failNext = false;
        throw new IllegalStateException("GitHub answered 503");
      }
      opened.add(change);
      return "https://github.com/krizaka/orazaka-packs/pull/" + opened.size();
    }
  }
}
