package com.krizaka.orazaka.tools.application.service;

import com.krizaka.orazaka.tools.application.port.law.RuleProposalStore;
import com.krizaka.orazaka.tools.domain.model.law.ProposalStatus;
import com.krizaka.orazaka.tools.domain.model.law.RuleProposal;
import com.krizaka.orazaka.tools.domain.model.law.VettedProposal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code rule_proposal} in memory, with the store's revision and supersede semantics. */
final class InMemoryRuleProposals implements RuleProposalStore {

  final List<RuleProposal> rows = new ArrayList<>();
  int writes;

  @Override
  public synchronized List<RuleProposal> record(
      String jurisdiction,
      String kindId,
      UUID derivationRunId,
      List<VettedProposal> proposals,
      Instant at) {
    writes++;
    List<RuleProposal> written = new ArrayList<>();
    for (VettedProposal p : proposals) {
      String ruleId = p.rule().ruleId();
      int revision =
          rows.stream()
                  .filter(r -> r.kindId().equals(kindId) && r.ruleId().equals(ruleId))
                  .mapToInt(RuleProposal::proposalRevision)
                  .max()
                  .orElse(0)
              + 1;
      if (p.status() == ProposalStatus.PROPOSED) {
        rows.replaceAll(
            r ->
                r.kindId().equals(kindId)
                        && r.ruleId().equals(ruleId)
                        && r.status() == ProposalStatus.PROPOSED
                    ? new RuleProposal(
                        r.id(),
                        r.jurisdiction(),
                        r.kindId(),
                        r.ruleId(),
                        r.proposalRevision(),
                        r.rule(),
                        r.derivationRunId(),
                        ProposalStatus.SUPERSEDED,
                        r.vetReport(),
                        null,
                        null,
                        null,
                        null,
                        r.createdAt())
                    : r);
      }
      RuleProposal row =
          new RuleProposal(
              UUID.randomUUID(),
              jurisdiction,
              kindId,
              ruleId,
              revision,
              p.rule(),
              derivationRunId,
              p.status(),
              p.report(),
              null,
              null,
              null,
              null,
              at);
      rows.add(row);
      written.add(row);
    }
    return written;
  }

  @Override
  public Optional<RuleProposal> find(UUID id) {
    return rows.stream().filter(r -> r.id().equals(id)).findFirst();
  }

  @Override
  public List<RuleProposal> list(ProposalStatus status, String kindId) {
    return rows.stream()
        .filter(r -> status == null || r.status() == status)
        .filter(r -> kindId == null || r.kindId().equals(kindId))
        .sorted(Comparator.comparing(RuleProposal::createdAt).reversed())
        .toList();
  }
}
