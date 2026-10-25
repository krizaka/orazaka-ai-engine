package com.krizaka.orazaka.tools.application.service;

import com.krizaka.orazaka.tools.application.port.law.LawCorpusStore;
import com.krizaka.orazaka.tools.application.port.law.LawSettings;
import com.krizaka.orazaka.tools.domain.model.law.InForceArticles;
import com.krizaka.orazaka.tools.domain.model.law.LawArticle;
import com.krizaka.orazaka.tools.domain.model.law.LawArticleVersion;
import com.krizaka.orazaka.tools.domain.model.law.StatuteLookup;
import com.krizaka.orazaka.tools.domain.model.law.StatuteVersion;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code lookupStatute} with a point in time (ADR-089 §2): the text of an article as it was in
 * force on a day, from the dated corpus.
 *
 * <p>The version selected is the one with {@code in_force_from <= asOf < in_force_to}. When the
 * corpus holds none for that day (the article is unknown, or the oldest text we hold took effect
 * after the day asked, or the article was not in force then), the answer is {@code
 * LAW_VERSION_UNKNOWN}: a later text is never read in place of an earlier one.
 */
public class LawStatuteLookupService {

  private final LawCorpusStore store;
  private final LawSettings settings;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param store the corpus
   * @param settings the {@code law.*} settings
   * @param clock the clock for a lookup with no day (injected)
   */
  public LawStatuteLookupService(LawCorpusStore store, LawSettings settings, Clock clock) {
    this.store = store;
    this.settings = settings;
    this.clock = clock;
  }

  /**
   * Looks an article up.
   *
   * @param jurisdiction {@code CA} or {@code QC}
   * @param instrument the instrument code, such as {@code CCQ-1991}
   * @param ref the article reference, such as {@code 1931} or {@code 169(4)}
   * @param language {@code fr} or {@code en}
   * @param asOf the day the text must be in force; null means today
   * @return the version in force, {@code LAW_VERSION_UNKNOWN}, or {@code LAW_CORPUS_DISABLED}
   * @throws IllegalArgumentException when a part of the question is blank or the language is not
   *     {@code fr} or {@code en}
   */
  public StatuteLookup lookupStatute(
      String jurisdiction, String instrument, String ref, String language, LocalDate asOf) {
    if (isBlank(jurisdiction) || isBlank(instrument) || isBlank(ref) || isBlank(language)) {
      throw new IllegalArgumentException(
          "jurisdiction, instrument, reference and language are required");
    }
    String lang = language.trim().toLowerCase(Locale.ROOT);
    if (!"fr".equals(lang) && !"en".equals(lang)) {
      throw new IllegalArgumentException("language must be fr or en");
    }
    if (!settings.corpusEnabled()) {
      return StatuteLookup.disabled();
    }
    LocalDate day = asOf != null ? asOf : LocalDate.now(clock);
    Optional<LawArticleVersion> version =
        store.inForceOn(
            jurisdiction.trim().toUpperCase(Locale.ROOT), instrument.trim(), ref.trim(), lang, day);
    return version
        .map(LawStatuteLookupService::toVersion)
        .map(StatuteLookup::found)
        .orElseGet(StatuteLookup::unknown);
  }

  private static StatuteVersion toVersion(LawArticleVersion stored) {
    LawArticle article = stored.article();
    return new StatuteVersion(
        article.text(),
        article.contentHash().hex(),
        article.inForceFrom(),
        article.officialUrl(),
        stored.importId());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /**
   * The corpus of one jurisdiction as it stood on a day, for the vet and the promotion: each
   * article as {@link #lookupStatute} finds it in force on that day.
   *
   * @param jurisdiction the jurisdiction
   * @param asOf the day
   * @return the articles in force on that day
   */
  public InForceArticles inForceOn(String jurisdiction, LocalDate asOf) {
    return (instrument, ref, language) -> {
      StatuteLookup found = lookupStatute(jurisdiction, instrument, ref, language, asOf);
      return found.outcome() == StatuteLookup.Outcome.FOUND
          ? Optional.of(found.version())
          : Optional.empty();
    };
  }
}
