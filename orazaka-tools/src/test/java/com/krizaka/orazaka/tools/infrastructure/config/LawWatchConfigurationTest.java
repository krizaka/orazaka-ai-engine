package com.krizaka.orazaka.tools.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.orazaka.tools.application.port.law.LawCorpusStore;
import com.krizaka.orazaka.tools.domain.model.law.LawArticleVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LawWatchConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(LawWatchConfiguration.class);

  @Test
  void the_watcher_is_off_by_default() {
    runner.run(context -> assertThat(context).doesNotHaveBean(LawWatchJob.class));
  }

  @Test
  void enabled_it_wires_the_job_with_its_store_and_token() {
    runner
        .withBean(LawCorpusStore.class, EmptyStore::new)
        .withPropertyValues(
            "orazaka.law.watcher.enabled=true", "orazaka.law.watcher.github.token=test-token")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(LawWatchJob.class);
              assertThat(context.getBean(LawWatchProperties.class).cron())
                  .isEqualTo("0 0 6 * * MON");
            });
  }

  @Test
  void enabled_without_a_token_refuses_to_start() {
    runner
        .withBean(LawCorpusStore.class, EmptyStore::new)
        .withPropertyValues("orazaka.law.watcher.enabled=true")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void enabled_without_a_corpus_store_refuses_to_start() {
    runner
        .withPropertyValues(
            "orazaka.law.watcher.enabled=true", "orazaka.law.watcher.github.token=test-token")
        .run(context -> assertThat(context).hasFailed());
  }

  /** An empty corpus: the wiring is under test, not the storage. */
  private static final class EmptyStore implements LawCorpusStore {
    @Override
    public List<LawArticleVersion> currentVersions(String instrument) {
      return List.of();
    }

    @Override
    public void add(LawArticleVersion version) {}

    @Override
    public void close(String instrument, String ref, String language, LocalDate inForceTo) {}

    @Override
    public void touch(String instrument, String ref, String language, Instant seenAt) {}
  }
}
