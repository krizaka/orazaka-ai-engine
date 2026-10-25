package com.krizaka.orazaka.tools.infrastructure.config;

import com.krizaka.orazaka.tools.application.port.law.LawSettings;
import com.krizaka.orazaka.tools.application.port.law.RuleDerivationModel;
import com.krizaka.orazaka.tools.application.port.law.RuleProposalStore;
import com.krizaka.orazaka.tools.application.service.LawRetrievalService;
import com.krizaka.orazaka.tools.application.service.LawStatuteLookupService;
import com.krizaka.orazaka.tools.application.service.RuleDerivationService;
import com.krizaka.orazaka.tools.application.service.RuleProposalService;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires stage 3 of ADR-089 (the derivation of proposed rules, {@code vet} and {@code
 * rule_proposal}) on top of {@link LawRetrievalConfiguration}. Off by default: {@code
 * orazaka.law.derivation.enabled=true} registers it, and even then nothing runs while {@code
 * law.derive.max-credits} is 0 or the retrieval is off.
 *
 * <p>It fails closed. The derivation model is supplied by the host; until one is wired a derivation
 * that reaches the model is refused, never answered by a stand-in.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "orazaka.law.derivation", name = "enabled", havingValue = "true")
public class RuleDerivationConfiguration {

  @Bean
  RuleProposalService ruleProposalService(RuleProposalStore store) {
    return new RuleProposalService(store, Clock.systemUTC());
  }

  @Bean
  RuleDerivationService ruleDerivationService(
      LawSettings settings,
      LawRetrievalService retrievals,
      LawStatuteLookupService lookups,
      ObjectProvider<RuleDerivationModel> model,
      RuleProposalService proposals) {
    return new RuleDerivationService(
        settings, retrievals, lookups, model.getIfAvailable(UnwiredModel::new), proposals);
  }

  /**
   * No model: a derivation is refused before anything is asked or charged. The service reads the
   * ceiling before the retrieval, so refusing there spends no retrieval on a call that cannot run.
   */
  static final class UnwiredModel implements RuleDerivationModel {

    @Override
    public long callCeiling() {
      throw new IllegalStateException("no derivation model is wired");
    }

    @Override
    public Answer propose(Prompt prompt) {
      throw new IllegalStateException("no derivation model is wired");
    }
  }
}
