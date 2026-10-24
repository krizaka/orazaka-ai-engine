package com.krizaka.orazaka.tools.infrastructure.config;

import com.krizaka.orazaka.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.orazaka.tools.application.port.law.LawChangeRequests;
import com.krizaka.orazaka.tools.application.port.law.LawCorpusStore;
import com.krizaka.orazaka.tools.application.port.law.LawFileReader;
import com.krizaka.orazaka.tools.application.port.law.LawSettings;
import com.krizaka.orazaka.tools.application.port.law.LawSource;
import com.krizaka.orazaka.tools.application.service.LawImportService;
import com.krizaka.orazaka.tools.application.service.LawManualImportService;
import com.krizaka.orazaka.tools.application.service.LawStatuteLookupService;
import com.krizaka.orazaka.tools.infrastructure.adapter.law.JusticeLawsXmlSource;
import com.krizaka.orazaka.tools.infrastructure.adapter.law.LawStatuteXmlParser;
import com.krizaka.orazaka.tools.infrastructure.adapter.law.LawXmlFileReader;
import com.krizaka.orazaka.tools.infrastructure.adapter.law.QuebecOpenDataSource;
import com.krizaka.orazaka.tools.infrastructure.adapter.mcp.LookupStatuteTool;
import com.krizaka.orazaka.tools.infrastructure.adapter.mcp.LookupStatuteToolRegistration;
import com.krizaka.orazaka.tools.infrastructure.law.RuntimeConfigLawSettings;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Wires the law corpus (ADR-089 §2): the {@code law.*} settings, the importers of the official
 * sources, the shared import, the manual import and {@code lookupStatute}. Nothing here reads the
 * network or writes the corpus until a caller asks, and every call checks {@code
 * law.corpus.enabled}. The scheduled watcher is {@link LawWatchConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LawWatchProperties.class)
public class LawCorpusConfiguration {

  /** Identifies the importers to the publishers, as ADR-087 asks: never a browser disguise. */
  static final String USER_AGENT = "OrazakaLawWatcher/0.1 (+https://www.orazaka.com)";

  @Bean
  LawSettings lawSettings(RuntimeConfigProvider config) {
    return new RuntimeConfigLawSettings(config);
  }

  @Bean
  LawStatuteXmlParser lawStatuteXmlParser() {
    return new LawStatuteXmlParser();
  }

  @Bean
  LawFileReader lawFileReader(LawStatuteXmlParser parser) {
    return new LawXmlFileReader(parser);
  }

  @Bean
  LawSource quebecOpenDataSource(LawWatchProperties properties, LawStatuteXmlParser parser) {
    return new QuebecOpenDataSource(openDataClient(), properties.quebec().datasetUrl(), parser);
  }

  @Bean
  LawSource justiceLawsXmlSource(LawStatuteXmlParser parser) {
    return new JusticeLawsXmlSource(openDataClient(), parser);
  }

  private static RestClient openDataClient() {
    return RestClient.builder().defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build();
  }

  @Bean
  LawImportService lawImportService(
      LawCorpusStore store, LawSettings settings, ObjectProvider<LawChangeRequests> requests) {
    return new LawImportService(store, settings, requests.getIfAvailable(), Clock.systemUTC());
  }

  @Bean
  LawManualImportService lawManualImportService(
      List<LawSource> sources,
      LawSettings settings,
      LawFileReader reader,
      LawImportService importer) {
    return new LawManualImportService(sources, settings, reader, importer);
  }

  @Bean
  LawStatuteLookupService lawStatuteLookupService(LawCorpusStore store, LawSettings settings) {
    return new LawStatuteLookupService(store, settings, Clock.systemUTC());
  }

  @Bean
  @ConditionalOnProperty(prefix = "orazaka.law.lookup", name = "enabled", havingValue = "true")
  LookupStatuteToolRegistration lookupStatuteToolRegistration(
      ToolRegistry registry, LawStatuteLookupService lookups) {
    return new LookupStatuteToolRegistration(registry, new LookupStatuteTool(lookups));
  }
}
