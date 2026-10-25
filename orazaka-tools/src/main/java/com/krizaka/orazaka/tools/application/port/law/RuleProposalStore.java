package com.krizaka.orazaka.tools.application.port.law;

import com.krizaka.orazaka.tools.domain.model.law.ProposalStatus;
import com.krizaka.orazaka.tools.domain.model.law.RuleProposal;
import com.krizaka.orazaka.tools.domain.model.law.VettedProposal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code rule_proposal} (job-service): every rule a derivation proposed, vetted, one row per
 * revision. It takes only {@link VettedProposal}s, so nothing reaches it without the vet; only
 * {@code RuleProposalService} calls it (rule LAW-004).
 */
public interface RuleProposalStore {

  /**
   * Writes the vetted rules of one derivation, in one transaction, locked per jurisdiction and kind
   * so that two derivations of the same kind cannot take the same revision. Each rule gets the next
   * {@code proposal_revision} of its rule id; the unreviewed {@code PROPOSED} revisions of the same
   * rule become {@code SUPERSEDED}.
   *
   * @param jurisdiction the jurisdiction
   * @param kindId the kind
   * @param derivationRunId the derivation
   * @param proposals the vetted rules, in order
   * @param at the instant written
   * @return the rows written, in the same order
   */
  List<RuleProposal> record(
      String jurisdiction,
      String kindId,
      UUID derivationRunId,
      List<VettedProposal> proposals,
      Instant at);

  /**
   * One row.
   *
   * @param id the row id
   * @return the row, if any
   */
  Optional<RuleProposal> find(UUID id);

  /**
   * The queue, newest first.
   *
   * @param status only this status, or every status when null
   * @param kindId only this kind, or every kind when null
   * @return the rows
   */
  List<RuleProposal> list(ProposalStatus status, String kindId);
}
