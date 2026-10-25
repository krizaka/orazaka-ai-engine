package com.krizaka.orazaka.tools.infrastructure.law;

import com.krizaka.orazaka.tools.application.port.law.RuleProposalStore;
import com.krizaka.orazaka.tools.domain.model.law.ProposalStatus;
import com.krizaka.orazaka.tools.domain.model.law.RuleProposal;
import com.krizaka.orazaka.tools.domain.model.law.VettedProposal;
import com.krizaka.orazaka.tools.infrastructure.adapter.law.ProposedRulesJson;
import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.entity.RuleProposalEntity;
import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.repository.RuleProposalRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link RuleProposalStore} on the job-service's {@code rule_proposal} table, through Spring Data
 * JPA on the engine's own data source (ADR-083). The rule, its law and its vet report are JSONB.
 * The revisions of one jurisdiction and kind are written under a transaction-scoped advisory lock,
 * so « read the highest revision, then insert the next » cannot interleave.
 */
@Component
class JpaRuleProposalStore implements RuleProposalStore {

  private final RuleProposalRepository proposals;

  JpaRuleProposalStore(RuleProposalRepository proposals) {
    this.proposals = Objects.requireNonNull(proposals, "RuleProposalRepository");
  }

  @Override
  @Transactional
  public List<RuleProposal> record(
      String jurisdiction,
      String kindId,
      UUID derivationRunId,
      List<VettedProposal> vetted,
      Instant at) {
    proposals.lockKind("rule_proposal|" + jurisdiction + "|" + kindId);
    List<RuleProposal> written = new ArrayList<>();
    for (VettedProposal proposal : vetted) {
      // A rule the vet found without an id is still recorded, under a name the reviewer can see.
      String ruleId = ruleIdOf(proposal);
      int revision = proposals.maxRevision(jurisdiction, kindId, ruleId) + 1;
      if (proposal.status() == ProposalStatus.PROPOSED) {
        proposals.supersede(jurisdiction, kindId, ruleId);
      }
      UUID id = UUID.randomUUID();
      proposals.insert(
          id,
          jurisdiction,
          kindId,
          ruleId,
          revision,
          ProposedRulesJson.write(proposal.rule()),
          ProposedRulesJson.write(proposal.rule().law()),
          derivationRunId,
          proposal.status().name(),
          ProposedRulesJson.write(proposal.report()),
          at);
      written.add(
          new RuleProposal(
              id,
              jurisdiction,
              kindId,
              ruleId,
              revision,
              proposal.rule(),
              derivationRunId,
              proposal.status(),
              proposal.report(),
              null,
              null,
              null,
              null,
              at));
    }
    return List.copyOf(written);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RuleProposal> find(UUID id) {
    return proposals.findById(id).map(JpaRuleProposalStore::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<RuleProposal> list(ProposalStatus status, String kindId) {
    return proposals.queue(status == null ? null : status.name(), kindId).stream()
        .map(JpaRuleProposalStore::toDomain)
        .toList();
  }

  /** The id the model gave, or a placeholder for a rule rejected for having none. */
  private static String ruleIdOf(VettedProposal proposal) {
    String ruleId = proposal.rule().ruleId();
    if (ruleId == null || ruleId.isBlank()) {
      return "UNNAMED";
    }
    return ruleId.length() <= 120 ? ruleId : ruleId.substring(0, 120);
  }

  private static RuleProposal toDomain(RuleProposalEntity row) {
    return new RuleProposal(
        row.getId(),
        row.getJurisdiction(),
        row.getKindId(),
        row.getRuleId(),
        row.getProposalRevision(),
        ProposedRulesJson.rule(row.getRule()),
        row.getDerivationRunId(),
        ProposalStatus.valueOf(row.getStatus()),
        ProposedRulesJson.report(row.getVetReport()),
        row.getReviewedBy(),
        row.getReviewedAt(),
        row.getDecisionNote(),
        row.getPacksPrUrl(),
        row.getCreatedAt());
  }
}
