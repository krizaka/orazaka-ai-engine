package com.krizaka.orazaka.tools.application.port.law;

import com.krizaka.orazaka.tools.domain.model.law.LawArticleVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The dated, hashed corpus of the law. Each article keeps every version it had; only one version
 * per article and language is current at a time.
 */
public interface LawCorpusStore {

  /**
   * The versions in force (not yet closed) of an instrument, all references and languages.
   *
   * @param instrument the instrument code
   * @return the current versions; empty when the instrument was never imported
   */
  List<LawArticleVersion> currentVersions(String instrument);

  /**
   * Adds a version as the current one.
   *
   * @param version the version to store
   */
  void add(LawArticleVersion version);

  /**
   * Closes the current version of an article.
   *
   * @param instrument the instrument code
   * @param ref the article reference
   * @param language the language
   * @param inForceTo the day it stopped being in force
   */
  void close(String instrument, String ref, String language, LocalDate inForceTo);

  /**
   * Records that the current version of an article was confirmed by an import.
   *
   * @param instrument the instrument code
   * @param ref the article reference
   * @param language the language
   * @param seenAt the import instant
   */
  void touch(String instrument, String ref, String language, Instant seenAt);
}
