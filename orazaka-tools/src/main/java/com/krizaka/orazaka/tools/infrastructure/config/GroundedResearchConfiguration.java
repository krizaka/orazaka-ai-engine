package com.krizaka.orazaka.tools.infrastructure.config;

import com.krizaka.orazaka.core.domain.ports.inbound.AiClient;
import com.krizaka.orazaka.core.domain.ports.inbound.CitationVerifier;
import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import com.krizaka.orazaka.tools.application.port.law.GroundedClaimsModel;
import com.krizaka.orazaka.tools.application.port.law.GroundedResearchSettings;
import com.krizaka.orazaka.tools.application.port.law.LawCorpusStore;
import com.krizaka.orazaka.tools.application.port.law.ResearchMeter;
import com.krizaka.orazaka.tools.application.service.GroundedResearchService;
import com.krizaka.orazaka.tools.application.service.LawCorpusResearchService;
import com.krizaka.orazaka.tools.domain.port.ResearchStore;
import com.krizaka.orazaka.tools.infrastructure.adapter.law.ChatGroundedClaimsModel;
import com.krizaka.orazaka.tools.infrastructure.law.RuntimeConfigGroundedResearchSettings;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@code research.grounded} capability (ADR-087 slice 5) on top of {@link
 * LawCorpusConfiguration}. Off by default: {@code orazaka.law.grounded.enabled=true} registers
 * {@link GroundedResearchService}, which the job service's executor for the handler key {@code
 * research.grounded} calls. Even then every call is refused while {@code research.enabled}, {@code
 * law.corpus.enabled} or the {@code law-corpus} source is off, and a call with a document while
 * {@code law.lecture.enabled} is off (all seeded off).
 *
 * <p>It fails closed: without a metering bean a call is refused before anything is asked.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "orazaka.law.grounded", name = "enabled", havingValue = "true")
public class GroundedResearchConfiguration {

  @Bean
  GroundedResearchSettings groundedResearchSettings(RuntimeConfigProvider config) {
    return new RuntimeConfigGroundedResearchSettings(config);
  }

  @Bean
  GroundedResearchService groundedResearchService(
      GroundedResearchSettings settings,
      ResearchStore research,
      LawCorpusStore corpus,
      ObjectProvider<GroundedClaimsModel> model,
      AiClient chat,
      CitationVerifier verifier,
      ObjectProvider<ResearchMeter> meter) {
    return new GroundedResearchService(
        settings,
        research,
        new LawCorpusResearchService(corpus, research, Clock.systemUTC()),
        model.getIfAvailable(() -> new ChatGroundedClaimsModel(chat)),
        verifier,
        meter.getIfAvailable(GroundedResearchConfiguration::noMeter));
  }

  private static ResearchMeter noMeter() {
    return (runId, actorId, providerKey) -> {
      throw new ResearchMeter.BudgetExceededException(
          "no metering is wired: a grounded call is never made unmetered");
    };
  }
}
