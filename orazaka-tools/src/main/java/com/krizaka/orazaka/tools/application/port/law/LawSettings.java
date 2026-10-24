package com.krizaka.orazaka.tools.application.port.law;

import com.krizaka.orazaka.tools.domain.model.law.LawTarget;
import java.util.List;

/**
 * The {@code law.*} settings in force, read from {@code orazaka_runtime_config} at call time: the
 * administrator changes them without a restart. Nothing is hard-coded by the callers.
 */
public interface LawSettings {

  /**
   * The master switch of the corpus ({@code law.corpus.enabled}).
   *
   * @return true when imports and lookups run
   */
  boolean corpusEnabled();

  /**
   * The instruments the corpus stores ({@code law.import.instruments}); nothing else is stored.
   *
   * @return one whole-instrument target per entry, in the setting's order
   */
  List<LawTarget> importInstruments();

  /**
   * The longest article text stored ({@code law.import.max-article-chars}).
   *
   * @return the limit in characters
   */
  int maxArticleChars();
}
