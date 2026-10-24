package com.krizaka.orazaka.tools.infrastructure.config;

import com.krizaka.orazaka.tools.application.port.law.LawChangeRequests;
import com.krizaka.orazaka.tools.application.port.law.LawSettings;
import com.krizaka.orazaka.tools.application.port.law.LawSource;
import com.krizaka.orazaka.tools.application.service.LawImportService;
import com.krizaka.orazaka.tools.application.service.LawWatchService;
import com.krizaka.orazaka.tools.infrastructure.adapter.law.GitHubLawChangeRequests;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * Wires the law watcher on top of {@link LawCorpusConfiguration}. Off by default; {@code
 * orazaka.law.watcher.enabled=true} starts the schedule, and then the corpus store (the job-service
 * law tables) must exist, as {@link LawCorpusConfiguration} needs it. What the watcher imports is
 * read from the {@code law.import.instruments} setting at each run, and it does nothing while
 * {@code law.corpus.enabled} is false.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(LawWatchProperties.class)
@ConditionalOnProperty(prefix = "orazaka.law.watcher", name = "enabled", havingValue = "true")
public class LawWatchConfiguration {

  @Bean
  LawChangeRequests lawChangeRequests(LawWatchProperties properties) {
    LawWatchProperties.GitHub github = properties.github();
    String token =
        Objects.requireNonNull(
            github.token(), "orazaka.law.watcher.github.token is required when the watcher is on");
    RestClient api =
        RestClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader(HttpHeaders.USER_AGENT, LawCorpusConfiguration.USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();
    return new GitHubLawChangeRequests(
        api, github.owner(), github.repository(), github.baseBranch());
  }

  @Bean
  LawWatchService lawWatchService(
      List<LawSource> sources, LawSettings settings, LawImportService importer) {
    return new LawWatchService(sources, settings, importer);
  }

  @Bean
  LawWatchJob lawWatchJob(LawWatchService service) {
    return new LawWatchJob(service);
  }
}
