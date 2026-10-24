package com.krizaka.orazaka.tools.application.service;

import com.krizaka.orazaka.tools.application.port.law.LawChangeRequests;
import com.krizaka.orazaka.tools.application.port.law.LawCorpusStore;
import com.krizaka.orazaka.tools.application.port.law.LawSource;
import com.krizaka.orazaka.tools.domain.model.law.LawArticle;
import com.krizaka.orazaka.tools.domain.model.law.LawArticleVersion;
import com.krizaka.orazaka.tools.domain.model.law.LawChange;
import com.krizaka.orazaka.tools.domain.model.law.LawTarget;
import com.krizaka.orazaka.tools.domain.model.law.LawWatchReport;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The law watcher: reads the targeted articles from the official sources, compares them with the
 * dated, hashed corpus and turns each difference into one review request.
 *
 * <p>The first import of an instrument only stores its texts (there is nothing to compare with).
 * After that, an added, changed or removed article opens one request. A change is applied to the
 * corpus only once its request is open, so a failed request is detected again by the next run.
 */
public class LawWatchService {

  private final List<LawSource> sources;
  private final List<LawTarget> targets;
  private final LawCorpusStore store;
  private final LawChangeRequests requests;
  private final Clock clock;

  /**
   * Creates the watcher.
   *
   * @param sources the official sources, each serving some jurisdictions
   * @param targets the articles to follow
   * @param store the corpus
   * @param requests where the changes are reviewed
   * @param clock the clock of the run (injected, never read from the system)
   */
  public LawWatchService(
      List<LawSource> sources,
      List<LawTarget> targets,
      LawCorpusStore store,
      LawChangeRequests requests,
      Clock clock) {
    this.sources = List.copyOf(sources);
    this.targets = List.copyOf(targets);
    this.store = store;
    this.requests = requests;
    this.clock = clock;
  }

  /**
   * Runs one watch: fetch, compare, store, open the review requests.
   *
   * @return what was unchanged, stored, requested and failed
   * @throws IllegalStateException when a jurisdiction has no source
   */
  public LawWatchReport run() {
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, clock.getZone());
    Map<String, List<LawTarget>> byJurisdiction = new LinkedHashMap<>();
    for (LawTarget target : targets) {
      byJurisdiction.computeIfAbsent(target.jurisdiction(), j -> new ArrayList<>()).add(target);
    }
    int unchanged = 0;
    int stored = 0;
    List<String> urls = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    for (Map.Entry<String, List<LawTarget>> group : byJurisdiction.entrySet()) {
      LawSource source = sourceFor(group.getKey());
      List<LawArticle> articles = source.fetch(group.getKey(), group.getValue());
      for (LawTarget target : group.getValue()) {
        Plan plan;
        try {
          plan = reconcile(target, articles, now, today);
        } catch (IllegalStateException e) {
          failures.add("target " + target.instrument() + ": " + e.getMessage());
          continue;
        }
        unchanged += plan.unchanged();
        stored += plan.toStore().size();
        plan.toStore().forEach(article -> store.add(version(article, now)));
        for (LawChange change : plan.changes()) {
          try {
            urls.add(requests.open(change));
            apply(change, now);
          } catch (RuntimeException e) {
            failures.add(
                change.kind()
                    + " "
                    + change.subject().instrument()
                    + " "
                    + change.subject().ref()
                    + " ("
                    + change.subject().language()
                    + "): "
                    + e.getClass().getSimpleName());
          }
        }
      }
    }
    return new LawWatchReport(unchanged, stored, urls, failures);
  }

  private LawSource sourceFor(String jurisdiction) {
    return sources.stream()
        .filter(source -> source.serves(jurisdiction))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("no law source serves " + jurisdiction));
  }

  private Plan reconcile(
      LawTarget target, List<LawArticle> articles, Instant now, LocalDate today) {
    boolean baseline = !store.currentVersions(target.instrument()).isEmpty();
    Map<String, LawArticleVersion> current = new LinkedHashMap<>();
    for (LawArticleVersion version : store.currentVersions(target.instrument())) {
      if (target.refs().contains(version.article().ref())) {
        current.put(key(version.article()), version);
      }
    }
    List<LawChange> changes = new ArrayList<>();
    List<LawArticle> toStore = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    int unchanged = 0;
    for (LawArticle article : articles) {
      if (!article.instrument().equals(target.instrument())
          || !target.refs().contains(article.ref())) {
        continue;
      }
      String key = key(article);
      seen.add(key);
      LawArticleVersion previous = current.get(key);
      if (previous == null) {
        if (baseline) {
          changes.add(new LawChange(LawChange.Kind.ADDED, null, article, today));
        } else {
          toStore.add(article);
        }
      } else if (previous.article().contentHash().equals(article.contentHash())) {
        store.touch(article.instrument(), article.ref(), article.language(), now);
        unchanged++;
      } else {
        changes.add(new LawChange(LawChange.Kind.CHANGED, previous.article(), article, today));
      }
    }
    boolean sourceHasInstrument =
        articles.stream().anyMatch(article -> article.instrument().equals(target.instrument()));
    if (!current.isEmpty() && !sourceHasInstrument) {
      // An empty answer must not flag the whole instrument as removed: a human looks first.
      throw new IllegalStateException(
          "the source returned no article of " + target.instrument() + ", nothing was changed");
    }
    for (Map.Entry<String, LawArticleVersion> entry : current.entrySet()) {
      if (!seen.contains(entry.getKey())) {
        changes.add(new LawChange(LawChange.Kind.REMOVED, entry.getValue().article(), null, today));
      }
    }
    return new Plan(unchanged, toStore, changes);
  }

  private void apply(LawChange change, Instant now) {
    LawArticle subject = change.subject();
    switch (change.kind()) {
      case ADDED -> store.add(version(change.current(), now));
      case CHANGED -> {
        store.close(
            subject.instrument(),
            subject.ref(),
            subject.language(),
            change.current().inForceFrom());
        store.add(version(change.current(), now));
      }
      case REMOVED ->
          store.close(subject.instrument(), subject.ref(), subject.language(), change.detectedOn());
    }
  }

  private static LawArticleVersion version(LawArticle article, Instant now) {
    return new LawArticleVersion(article, now, now, null);
  }

  private static String key(LawArticle article) {
    return article.instrument() + "|" + article.ref() + "|" + article.language();
  }

  /**
   * The decisions of one target: what is confirmed, what is new on the first import, what moved.
   */
  private record Plan(int unchanged, List<LawArticle> toStore, List<LawChange> changes) {}
}
