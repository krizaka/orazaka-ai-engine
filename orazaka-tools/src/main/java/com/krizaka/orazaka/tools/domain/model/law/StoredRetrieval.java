package com.krizaka.orazaka.tools.domain.model.law;

import com.krizaka.orazaka.core.domain.model.research.Claim;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A row of {@code law_retrieval}: the cache of stage 2, keyed by jurisdiction, kind and the as-of
 * bucket (the {@code in_force_from} of the newest article it used).
 *
 * @param jurisdiction the jurisdiction
 * @param kindId the kind of document
 * @param asOfBucket the {@code in_force_from} of the newest article used
 * @param articles the articles used
 * @param claims the claims on them, with their verification status
 * @param runId the run that computed it, if any
 * @param createdAt when it was computed
 * @param stale true when a cited article changed since: the next call recomputes
 */
public record StoredRetrieval(
    String jurisdiction,
    String kindId,
    LocalDate asOfBucket,
    List<CitedArticle> articles,
    List<Claim> claims,
    UUID runId,
    Instant createdAt,
    boolean stale) {

  /** Copies the lists and checks the key. */
  public StoredRetrieval {
    Objects.requireNonNull(jurisdiction, "jurisdiction");
    Objects.requireNonNull(kindId, "kindId");
    Objects.requireNonNull(asOfBucket, "asOfBucket");
    Objects.requireNonNull(createdAt, "createdAt");
    articles = articles == null ? List.of() : List.copyOf(articles);
    claims = claims == null ? List.of() : List.copyOf(claims);
  }
}
