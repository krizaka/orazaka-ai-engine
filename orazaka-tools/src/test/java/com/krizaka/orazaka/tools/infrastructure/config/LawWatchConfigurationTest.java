package com.krizaka.orazaka.tools.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.orazaka.tools.application.port.law.LawChangeRequests;
import com.krizaka.orazaka.tools.application.port.law.LawCorpusStore;
import com.krizaka.orazaka.tools.application.service.LawImportService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LawWatchConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(LawCorpusConfiguration.class, LawWatchConfiguration.class)
          .withBean(RuntimeConfigProvider.class, LawCorpusConfigurationTest.OffConfig::new);

  @Test
  void the_watcher_is_off_by_default() {
    runner
        .withBean(LawCorpusStore.class, LawCorpusConfigurationTest.EmptyStore::new)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(LawWatchJob.class);
              assertThat(context).doesNotHaveBean(LawChangeRequests.class);
            });
  }

  @Test
  void enabled_it_wires_the_job_with_its_store_and_token() {
    runner
        .withBean(LawCorpusStore.class, LawCorpusConfigurationTest.EmptyStore::new)
        .withPropertyValues(
            "orazaka.law.watcher.enabled=true", "orazaka.law.watcher.github.token=test-token")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(LawWatchJob.class);
              assertThat(context).hasSingleBean(LawChangeRequests.class);
              assertThat(context.getBean(LawWatchProperties.class).cron())
                  .isEqualTo("0 0 6 * * MON");
              assertThat(context).hasSingleBean(LawImportService.class);
            });
  }

  @Test
  void enabled_without_a_token_refuses_to_start() {
    runner
        .withBean(LawCorpusStore.class, LawCorpusConfigurationTest.EmptyStore::new)
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
}
