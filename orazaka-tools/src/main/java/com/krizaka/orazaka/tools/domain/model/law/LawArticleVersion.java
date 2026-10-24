package com.krizaka.orazaka.tools.domain.model.law;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A stored version of an article. {@code inForceTo} is null while it is the current version.
 *
 * @param article the article as it was read
 * @param firstSeenAt when this exact text was first read
 * @param lastSeenAt when it was last confirmed by an import
 * @param inForceTo the day the next version took over, or the day it was removed; null if current
 */
public record LawArticleVersion(
    LawArticle article, Instant firstSeenAt, Instant lastSeenAt, LocalDate inForceTo) {

  /**
   * Returns the same version confirmed again at {@code seenAt}.
   *
   * @param seenAt the import instant
   * @return a copy with the new {@code lastSeenAt}
   */
  public LawArticleVersion seenAt(Instant seenAt) {
    return new LawArticleVersion(article, firstSeenAt, seenAt, inForceTo);
  }
}
