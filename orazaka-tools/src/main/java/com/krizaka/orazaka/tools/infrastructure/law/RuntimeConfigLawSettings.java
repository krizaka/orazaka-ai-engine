package com.krizaka.orazaka.tools.infrastructure.law;

import com.krizaka.orazaka.core.domain.model.research.VerifyMode;
import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.orazaka.tools.application.port.law.LawSettings;
import com.krizaka.orazaka.tools.domain.model.law.LawTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The {@code law.*} rows of {@code orazaka_runtime_config}, read at each call: the administrator
 * changes them without a restart, and the bounds are held by the table itself. When a row is
 * missing the corpus is off and stores nothing, the safe reading; the limit falls back to the value
 * the rows are seeded with.
 */
public final class RuntimeConfigLawSettings implements LawSettings {

  static final String KEY_ENABLED = "law.corpus.enabled";
  static final String KEY_INSTRUMENTS = "law.import.instruments";
  static final String KEY_MAX_ARTICLE_CHARS = "law.import.max-article-chars";
  static final String KEY_RESEARCH_ENABLED = "research.enabled";
  static final String KEY_RETRIEVAL_MAX_ARTICLES = "law.retrieval.max-articles";
  static final String KEY_RETRIEVAL_MAX_AGE_DAYS = "law.retrieval.max-age-days";
  static final String KEY_VERIFY_MODE = "research.verify.mode";

  /** The seeded defaults of the retrieval rows, when one is missing. */
  private static final int SEEDED_RETRIEVAL_MAX_ARTICLES = 40;

  private static final int SEEDED_RETRIEVAL_MAX_AGE_DAYS = 90;

  /** The seeded default of {@code law.import.max-article-chars}, when the row is missing. */
  private static final int SEEDED_MAX_ARTICLE_CHARS = 20_000;

  private final RuntimeConfigProvider config;

  /**
   * Creates the settings reader.
   *
   * @param config the runtime settings
   */
  public RuntimeConfigLawSettings(RuntimeConfigProvider config) {
    this.config = Objects.requireNonNull(config, "RuntimeConfigProvider cannot be null");
  }

  @Override
  public boolean corpusEnabled() {
    return config.getBoolean(KEY_ENABLED, false);
  }

  @Override
  public List<LawTarget> importInstruments() {
    List<LawTarget> targets = new ArrayList<>();
    for (String entry : config.getString(KEY_INSTRUMENTS, "").split(",")) {
      String trimmed = entry.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int colon = trimmed.indexOf(':');
      if (colon <= 0 || colon == trimmed.length() - 1) {
        throw new IllegalStateException(
            KEY_INSTRUMENTS + " entries are JURISDICTION:CODE, got a malformed one");
      }
      targets.add(
          LawTarget.wholeInstrument(trimmed.substring(0, colon), trimmed.substring(colon + 1)));
    }
    return List.copyOf(targets);
  }

  @Override
  public int maxArticleChars() {
    return config.getInt(KEY_MAX_ARTICLE_CHARS, SEEDED_MAX_ARTICLE_CHARS);
  }

  @Override
  public boolean researchEnabled() {
    return config.getBoolean(KEY_RESEARCH_ENABLED, false);
  }

  @Override
  public int retrievalMaxArticles() {
    return config.getInt(KEY_RETRIEVAL_MAX_ARTICLES, SEEDED_RETRIEVAL_MAX_ARTICLES);
  }

  @Override
  public int retrievalMaxAgeDays() {
    return config.getInt(KEY_RETRIEVAL_MAX_AGE_DAYS, SEEDED_RETRIEVAL_MAX_AGE_DAYS);
  }

  @Override
  public VerifyMode verifyMode() {
    return "STRICT".equalsIgnoreCase(config.getString(KEY_VERIFY_MODE, "MARK"))
        ? VerifyMode.STRICT
        : VerifyMode.MARK;
  }
}
