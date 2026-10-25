package com.krizaka.orazaka.tools.infrastructure.config;

import com.krizaka.orazaka.tools.application.port.law.LawSettings;
import com.krizaka.orazaka.tools.application.port.law.RuleDerivationModel;
import com.krizaka.orazaka.tools.application.port.law.RulesetPack;
import com.krizaka.orazaka.tools.application.service.KindDerivationService;
import com.krizaka.orazaka.tools.application.service.LawRetrievalService;
import com.krizaka.orazaka.tools.application.service.LawStatuteLookupService;
import com.krizaka.orazaka.tools.application.service.RuleDerivationService;
import com.krizaka.orazaka.tools.application.service.RuleProposalService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires stage 3 of ADR-089 (the derivation of proposed rules, {@code vet} and {@code
 * rule_proposal}) on top of {@link LawRetrievalConfiguration} and {@link LawReviewConfiguration}
 * (which owns {@code RuleProposalService}). Off by default: {@code
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
  RuleDerivationService ruleDerivationService(
      LawSettings settings,
      LawRetrievalService retrievals,
      LawStatuteLookupService lookups,
      ObjectProvider<RuleDerivationModel> model,
      RuleProposalService proposals) {
    return new RuleDerivationService(
        settings, retrievals, lookups, model.getIfAvailable(UnwiredModel::new), proposals);
  }

  @Bean
  KindDerivationService kindDerivationService(
      ObjectProvider<RulesetPack> pack, RuleDerivationService derivations) {
    return new KindDerivationService(
        pack.getIfAvailable(LawReviewConfiguration.UnwiredPack::new), derivations);
  }

  /** No model: a call is refused before anything is asked or charged. */
  static final class UnwiredModel implements RuleDerivationModel {

    @Override
    public long callCeiling() {
      return 1;
    }

    @Override
    public Answer propose(Prompt prompt) {
      throw new IllegalStateException("no derivation model is wired");
    }
  }
}
