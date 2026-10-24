package com.krizaka.orazaka.tools.infrastructure.law;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.orazaka.tools.domain.model.law.LawTarget;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeConfigLawSettingsTest {

  private final Map<String, String> rows = new HashMap<>();
  private final RuntimeConfigLawSettings settings =
      new RuntimeConfigLawSettings(new MapConfig(rows));

  @Test
  void the_corpus_is_off_and_empty_until_the_rows_say_otherwise() {
    assertThat(settings.corpusEnabled()).isFalse();
    assertThat(settings.importInstruments()).isEmpty();
  }

  @Test
  void the_instruments_are_read_from_the_row_at_each_call() {
    rows.put("law.corpus.enabled", "true");
    rows.put("law.import.instruments", "QC:CCQ-1991, CA:E-15 ,QC:T-15.01-r3");

    assertThat(settings.corpusEnabled()).isTrue();
    assertThat(settings.importInstruments())
        .containsExactly(
            LawTarget.wholeInstrument("QC", "CCQ-1991"),
            LawTarget.wholeInstrument("CA", "E-15"),
            LawTarget.wholeInstrument("QC", "T-15.01-r3"));

    rows.put("law.import.instruments", "CA:E-15");
    assertThat(settings.importInstruments())
        .containsExactly(LawTarget.wholeInstrument("CA", "E-15"));
  }

  @Test
  void a_malformed_entry_fails_loudly_rather_than_storing_the_wrong_thing() {
    rows.put("law.import.instruments", "CCQ-1991");

    assertThatThrownBy(settings::importInstruments).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void the_article_limit_is_the_row() {
    assertThat(settings.maxArticleChars()).isEqualTo(20_000);
    rows.put("law.import.max-article-chars", "5000");
    assertThat(settings.maxArticleChars()).isEqualTo(5_000);
  }

  /** A runtime config on a map: the keys are the rows of {@code orazaka_runtime_config}. */
  private record MapConfig(Map<String, String> rows) implements RuntimeConfigProvider {
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
      return rows.containsKey(key) ? Boolean.parseBoolean(rows.get(key)) : defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
      return rows.containsKey(key) ? Integer.parseInt(rows.get(key)) : defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
      return rows.getOrDefault(key, defaultValue);
    }
  }
}
