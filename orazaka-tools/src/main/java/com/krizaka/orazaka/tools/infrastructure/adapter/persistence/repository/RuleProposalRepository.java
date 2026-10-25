package com.krizaka.orazaka.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.entity.RuleProposalEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code rule_proposal}. */
public interface RuleProposalRepository extends JpaRepository<RuleProposalEntity, UUID> {

  /**
   * Takes the transaction's lock of one jurisdiction and kind: two derivations of the same kind
   * write their revisions one after the other. Released at commit or rollback.
   */
  @Query(
      value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:key))) AS locked",
      nativeQuery = true)
  Integer lockKind(@Param("key") String key);

  /** The highest revision of a rule, 0 when it was never proposed. */
  @Query(
      value =
          "SELECT COALESCE(MAX(proposal_revision), 0) FROM rule_proposal WHERE jurisdiction ="
              + " :jurisdiction AND kind_id = :kindId AND rule_id = :ruleId",
      nativeQuery = true)
  int maxRevision(
      @Param("jurisdiction") String jurisdiction,
      @Param("kindId") String kindId,
      @Param("ruleId") String ruleId);

  /**
   * The unreviewed proposals of a rule become {@code SUPERSEDED}: a later revision replaces them.
   */
  @Modifying
  @Query(
      value =
          "UPDATE rule_proposal SET status = 'SUPERSEDED' WHERE jurisdiction = :jurisdiction"
              + " AND kind_id = :kindId AND rule_id = :ruleId AND status = 'PROPOSED'",
      nativeQuery = true)
  int supersede(
      @Param("jurisdiction") String jurisdiction,
      @Param("kindId") String kindId,
      @Param("ruleId") String ruleId);

  /** Writes one revision. */
  @Modifying
  @Query(
      value =
          "INSERT INTO rule_proposal (id, jurisdiction, kind_id, rule_id, proposal_revision, rule,"
              + " law, derivation_run_id, status, vet_report, created_at) VALUES (:id,"
              + " :jurisdiction, :kindId, :ruleId, :revision, CAST(:rule AS jsonb), CAST(:law AS"
              + " jsonb), :runId, :status, CAST(:vetReport AS jsonb), :createdAt)",
      nativeQuery = true)
  int insert(
      @Param("id") UUID id,
      @Param("jurisdiction") String jurisdiction,
      @Param("kindId") String kindId,
      @Param("ruleId") String ruleId,
      @Param("revision") int revision,
      @Param("rule") String rule,
      @Param("law") String law,
      @Param("runId") UUID runId,
      @Param("status") String status,
      @Param("vetReport") String vetReport,
      @Param("createdAt") Instant createdAt);

  /** The queue, filtered by status and kind when they are given, newest first. */
  @Query(
      "SELECT p FROM RuleProposalEntity p WHERE (:status IS NULL OR p.status = :status)"
          + " AND (:kindId IS NULL OR p.kindId = :kindId)"
          + " ORDER BY p.createdAt DESC, p.ruleId, p.proposalRevision DESC")
  List<RuleProposalEntity> queue(@Param("status") String status, @Param("kindId") String kindId);
}
