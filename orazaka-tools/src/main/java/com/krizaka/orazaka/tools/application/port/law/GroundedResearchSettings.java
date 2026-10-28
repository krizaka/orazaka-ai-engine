package com.krizaka.orazaka.tools.application.port.law;

import com.krizaka.orazaka.core.domain.model.research.VerifyMode;

/**
 * The settings of the {@code research.grounded} capability (ADR-087 slice 5, ADR-089 §6), read from
 * {@code orazaka_runtime_config} at each call so that an administrator's change applies at once.
 * Every switch is off as seeded.
 */
public interface GroundedResearchSettings {

  /**
   * The master switch of grounded research ({@code research.enabled}).
   *
   * @return whether research may run at all
   */
  boolean researchEnabled();

  /**
   * Whether the {@code law-corpus} provider may be read ({@code law.corpus.enabled}).
   *
   * @return true when the corpus is switched on
   */
  boolean corpusEnabled();

  /**
   * Whether the unaudited « lecture » of a document may run ({@code law.lecture.enabled}).
   *
   * @return true when a document may be read against the law
   */
  boolean lectureEnabled();

  /**
   * What happens to a claim that is not verified ({@code research.verify.mode}).
   *
   * @return keep it marked, or drop it
   */
  VerifyMode verifyMode();

  /**
   * The most articles one call reads ({@code law.retrieval.max-articles}).
   *
   * @return the bound
   */
  int maxArticles();

  /**
   * The longest quote a citation may carry ({@code research.quote.max-chars}).
   *
   * @return the bound, in characters
   */
  int quoteMaxChars();
}
