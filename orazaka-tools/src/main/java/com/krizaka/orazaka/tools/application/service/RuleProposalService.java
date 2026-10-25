package com.krizaka.orazaka.tools.application.service;

import com.krizaka.orazaka.tools.application.port.law.RuleProposalStore;
import com.krizaka.orazaka.tools.domain.model.law.ProposalStatus;
import com.krizaka.orazaka.tools.domain.model.law.RuleProposal;
import com.krizaka.orazaka.tools.domain.model.law.VettedProposal;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The writes and reads of {@code rule_proposal} (ADR-089 §3): the only door to the table. A rule
 * enters only vetted ({@link VettedProposal}), as {@code PROPOSED} or {@code REJECTED_AUTO}; a
 * proposal never becomes a ruleset here (promotion, stage 5, is another door).
 */
public class RuleProposalService {

  private final RuleProposalStore store;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param store {@code rule_proposal}
   * @param clock the clock (injected)
   */
  public RuleProposalService(RuleProposalStore store, Clock clock) {
    this.store = Objects.requireNonNull(store, "RuleProposalStore");
    this.clock = Objects.requireNonNull(clock, "Clock");
  }

  /**
   * Records the vetted rules of one derivation.
   *
   * @param jurisdiction the jurisdiction, such as {@code QC}
   * @param kindId the kind the rules are for
   * @param derivationRunId the derivation
   * @param proposals the vetted rules, in order; empty writes nothing
   * @return the rows written, in order
   * @throws IllegalArgumentException when the jurisdiction or the kind is blank
   */
  public List<RuleProposal> record(
      String jurisdiction, String kindId, UUID derivationRunId, List<VettedProposal> proposals) {
    if (jurisdiction == null || jurisdiction.isBlank() || kindId == null || kindId.isBlank()) {
      throw new IllegalArgumentException("a proposal names its jurisdiction and its kind");
    }
    Objects.requireNonNull(derivationRunId, "derivationRunId");
    if (proposals == null || proposals.isEmpty()) {
      return List.of();
    }
    return store.record(
        jurisdiction.trim().toUpperCase(Locale.ROOT),
        kindId.trim(),
        derivationRunId,
        List.copyOf(proposals),
        clock.instant());
  }

  /**
   * One proposal.
   *
   * @param id the row id
   * @return the row, if any
   */
  public Optional<RuleProposal> find(UUID id) {
    return store.find(Objects.requireNonNull(id, "id"));
  }

  /**
   * The review queue.
   *
   * @param status only this status, or all when null
   * @param kindId only this kind, or all when null or blank
   * @return the rows, newest first
   */
  public List<RuleProposal> list(ProposalStatus status, String kindId) {
    return store.list(status, kindId == null || kindId.isBlank() ? null : kindId.trim());
  }
}
