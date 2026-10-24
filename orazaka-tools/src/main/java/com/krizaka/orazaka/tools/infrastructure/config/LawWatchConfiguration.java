package com.krizaka.orazaka.tools.infrastructure.config;

import com.krizaka.orazaka.tools.application.port.law.LawChangeRequests;
import com.krizaka.orazaka.tools.application.port.law.LawCorpusStore;
import com.krizaka.orazaka.tools.application.port.law.LawSource;
import com.krizaka.orazaka.tools.application.service.LawWatchService;
import com.krizaka.orazaka.tools.domain.model.law.LawTarget;
import com.krizaka.orazaka.tools.infrastructure.adapter.law.GitHubLawChangeRequests;
import com.krizaka.orazaka.tools.infrastructure.adapter.law.JusticeLawsXmlSource;
import com.krizaka.orazaka.tools.infrastructure.adapter.law.LawStatuteXmlParser;
import com.krizaka.orazaka.tools.infrastructure.adapter.law.QuebecOpenDataSource;
import java.time.Clock;
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
 * Wires the law watcher. Off by default; {@code orazaka.law.watcher.enabled=true} starts it, and
 * then the {@link LawCorpusStore} bean (the law tables, ADR-089 cut item 2) must exist.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(LawWatchProperties.class)
@ConditionalOnProperty(prefix = "orazaka.law.watcher", name = "enabled", havingValue = "true")
public class LawWatchConfiguration {

  /** Identifies the watcher to the publishers, as ADR-087 asks: never a browser disguise. */
  static final String USER_AGENT = "OrazakaLawWatcher/0.1 (+https://www.orazaka.com)";

  @Bean
  LawWatchService lawWatchService(LawWatchProperties properties, LawCorpusStore store) {
    LawStatuteXmlParser parser = new LawStatuteXmlParser();
    RestClient open =
        RestClient.builder().defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
    List<LawSource> sources =
        List.of(
            new QuebecOpenDataSource(open, properties.quebec().datasetUrl(), parser),
            new JusticeLawsXmlSource(open, parser));
    List<LawTarget> targets =
        properties.targets().stream()
            .map(scope -> LawTarget.of(scope.jurisdiction(), scope.instrument(), scope.refs()))
            .toList();
    LawChangeRequests requests = changeRequests(properties);
    return new LawWatchService(sources, targets, store, requests, Clock.systemUTC());
  }

  @Bean
  LawWatchJob lawWatchJob(LawWatchService service) {
    return new LawWatchJob(service);
  }

  private static LawChangeRequests changeRequests(LawWatchProperties properties) {
    LawWatchProperties.GitHub github = properties.github();
    String token =
        Objects.requireNonNull(
            github.token(), "orazaka.law.watcher.github.token is required when the watcher is on");
    RestClient api =
        RestClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();
    return new GitHubLawChangeRequests(
        api, github.owner(), github.repository(), github.baseBranch());
  }
}
