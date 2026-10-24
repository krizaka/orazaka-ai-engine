package com.krizaka.orazaka.tools.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.orazaka.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.orazaka.tools.application.port.law.LawCorpusStore;
import com.krizaka.orazaka.tools.application.port.law.LawSource;
import com.krizaka.orazaka.tools.application.service.LawManualImportService;
import com.krizaka.orazaka.tools.application.service.LawStatuteLookupService;
import com.krizaka.orazaka.tools.domain.model.law.LawArticle;
import com.krizaka.orazaka.tools.domain.model.law.LawArticleVersion;
import com.krizaka.orazaka.tools.domain.model.law.LawImportResult;
import com.krizaka.orazaka.tools.domain.model.law.LawImportStart;
import com.krizaka.orazaka.tools.domain.model.law.LookupStatuteRequest;
import com.krizaka.orazaka.tools.domain.model.law.LookupStatuteResponse;
import com.krizaka.orazaka.tools.infrastructure.adapter.mcp.LookupStatuteTool;
import com.krizaka.orazaka.tools.infrastructure.adapter.mcp.LookupStatuteToolRegistration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LawCorpusConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(LawCorpusConfiguration.class)
          .withBean(RuntimeConfigProvider.class, OffConfig::new)
          .withBean(LawCorpusStore.class, EmptyStore::new);

  @Test
  void the_corpus_services_and_both_importers_are_wired_but_the_tool_is_not_offered() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(LawManualImportService.class);
          assertThat(context).hasSingleBean(LawStatuteLookupService.class);
          assertThat(context.getBeansOfType(LawSource.class).values())
              .extracting(LawSource::sourceKey)
              .containsExactlyInAnyOrder("donnees-quebec", "justice-laws");
          assertThat(context).doesNotHaveBean(LookupStatuteToolRegistration.class);
        });
  }

  @Test
  void with_the_property_the_tool_is_registered_in_the_one_registry() {
    RecordingRegistry registry = new RecordingRegistry();
    runner
        .withBean(ToolRegistry.class, () -> registry)
        .withPropertyValues("orazaka.law.lookup.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(registry.names).containsExactly("lookupStatute");
              assertThat(registry.inputType).isEqualTo(LookupStatuteRequest.class);
            });
  }

  @Test
  void the_tool_answers_corpus_disabled_while_the_setting_is_off_and_invalid_for_a_bad_question() {
    runner.run(
        context -> {
          LookupStatuteTool tool =
              new LookupStatuteTool(context.getBean(LawStatuteLookupService.class));

          assertThat(
                  tool.apply(new LookupStatuteRequest("QC", "CCQ-1991", "1942", null, "fr"))
                      .outcome())
              .isEqualTo("LAW_CORPUS_DISABLED");
          assertThat(
                  tool.apply(new LookupStatuteRequest("QC", "CCQ-1991", "1942", "yesterday", "fr"))
                      .outcome())
              .isEqualTo("INVALID_REQUEST");
          assertThat(tool.apply(new LookupStatuteRequest("QC", "CCQ-1991", "", null, "fr")))
              .isEqualTo(LookupStatuteResponse.invalid());
        });
  }

  /** The runtime settings with no law row: everything off. */
  static final class OffConfig implements RuntimeConfigProvider {
    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
      return defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
      return defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
      return defaultValue;
    }
  }

  /** An empty corpus: the wiring is under test, not the storage. */
  static final class EmptyStore implements LawCorpusStore {
    @Override
    public UUID beginImport(LawImportStart start) {
      return UUID.randomUUID();
    }

    @Override
    public void finishImport(UUID importId, LawImportResult result) {}

    @Override
    public Optional<String> lastEtag(String sourceKey, String datasetUrl) {
      return Optional.empty();
    }

    @Override
    public List<LawArticleVersion> currentVersions(String jurisdiction, String instrument) {
      return List.of();
    }

    @Override
    public void add(UUID importId, String sourceKey, LawArticle article, Instant seenAt) {}

    @Override
    public void replace(UUID importId, LawArticle previous, LawArticle next, Instant seenAt) {}

    @Override
    public void close(LawArticle previous, LocalDate inForceTo) {}

    @Override
    public void touch(
        String jurisdiction, String instrument, Collection<LawArticle> articles, Instant seenAt) {}

    @Override
    public Optional<LawArticleVersion> inForceOn(
        String jurisdiction, String instrument, String ref, String language, LocalDate asOf) {
      return Optional.empty();
    }
  }

  private static final class RecordingRegistry implements ToolRegistry {
    private final List<String> names = new ArrayList<>();
    private Class<?> inputType;

    @Override
    public <I, O> void registerTool(
        String name, String description, Class<I> inputType, Function<I, O> function) {
      names.add(name);
      this.inputType = inputType;
      assertThat(description).contains("LAW_VERSION_UNKNOWN");
    }

    @Override
    public List<ToolCallback> getRegisteredTools() {
      return List.of();
    }
  }
}
