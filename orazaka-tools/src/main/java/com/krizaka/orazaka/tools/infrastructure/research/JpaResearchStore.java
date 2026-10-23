package com.krizaka.orazaka.tools.infrastructure.research;

import com.krizaka.orazaka.tools.domain.model.research.FetchAudit;
import com.krizaka.orazaka.tools.domain.model.research.FetchedDocument;
import com.krizaka.orazaka.tools.domain.model.research.ResearchSource;
import com.krizaka.orazaka.tools.domain.port.ResearchStore;
import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.entity.ResearchDocumentEntity;
import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.entity.ResearchDocumentId;
import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.entity.ResearchFetchEntity;
import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.entity.ResearchSourceEntity;
import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.repository.ResearchDocumentRepository;
import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.repository.ResearchFetchRepository;
import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.repository.ResearchSourceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** {@link ResearchStore} on the engine's tables, through Spring Data JPA (no SQL strings). */
@Component
class JpaResearchStore implements ResearchStore {

  private final ResearchSourceRepository sources;
  private final ResearchDocumentRepository documents;
  private final ResearchFetchRepository fetches;

  JpaResearchStore(
      ResearchSourceRepository sources,
      ResearchDocumentRepository documents,
      ResearchFetchRepository fetches) {
    this.sources = Objects.requireNonNull(sources, "ResearchSourceRepository cannot be null");
    this.documents = Objects.requireNonNull(documents, "ResearchDocumentRepository cannot be null");
    this.fetches = Objects.requireNonNull(fetches, "ResearchFetchRepository cannot be null");
  }

  @Override
  public Optional<ResearchSource> source(String sourceKey) {
    return sources.findBySourceKey(sourceKey).map(JpaResearchStore::toSource);
  }

  @Override
  public Optional<FetchedDocument> freshDocument(String url, Instant now) {
    return documents
        .findFirstByIdUrlAndExpiresAtAfterOrderByFetchedAtDesc(url, now)
        .map(JpaResearchStore::toDocument);
  }

  @Override
  public void saveDocument(FetchedDocument document, String requestedUrl, UUID sourceId) {
    documents.save(toEntity(document, document.url(), sourceId));
    if (!requestedUrl.equals(document.url())) {
      // A redirected URL is cached under the URL that was asked for too: the next ask is a hit.
      documents.save(toEntity(document, requestedUrl, sourceId));
    }
  }

  @Override
  public void audit(FetchAudit audit) {
    ResearchFetchEntity row = new ResearchFetchEntity();
    row.setAt(audit.at());
    row.setActorId(audit.actorId());
    row.setRunId(audit.runId());
    row.setChatId(audit.chatId());
    row.setSourceId(audit.sourceId());
    row.setOperation(audit.operation());
    row.setHost(audit.host());
    row.setPathHash(audit.pathHash());
    row.setHttpStatus(audit.httpStatus());
    row.setBytes(audit.bytes());
    row.setDurationMs(audit.durationMs());
    row.setCacheHit(audit.cacheHit());
    row.setRefusedReason(audit.refusal() == null ? null : audit.refusal().name());
    fetches.save(row);
  }

  private static ResearchSource toSource(ResearchSourceEntity e) {
    return new ResearchSource(
        e.getId(),
        e.getSourceKey(),
        e.getKind(),
        e.getProvider(),
        e.getAllowPatterns() == null ? List.of() : List.of(e.getAllowPatterns()),
        e.getDenyPatterns() == null ? List.of() : List.of(e.getDenyPatterns()),
        e.getAllowHttp(),
        e.getFreshnessSeconds(),
        e.getRatePerMinute(),
        e.getEnabled());
  }

  private static FetchedDocument toDocument(ResearchDocumentEntity e) {
    return new FetchedDocument(
        e.getId().getUrl(),
        e.getId().getContentHash(),
        e.getFetchedAt(),
        e.getHttpStatus(),
        e.getContentType(),
        e.getBytes(),
        e.getTitle(),
        e.getText(),
        e.getExpiresAt());
  }

  private static ResearchDocumentEntity toEntity(FetchedDocument d, String url, UUID sourceId) {
    ResearchDocumentEntity e = new ResearchDocumentEntity();
    e.setId(new ResearchDocumentId(url, d.contentHash()));
    e.setSourceId(sourceId);
    e.setFetchedAt(d.fetchedAt());
    e.setHttpStatus(d.httpStatus());
    e.setContentType(d.contentType());
    e.setBytes(d.bytes());
    e.setTitle(d.title());
    e.setText(d.text());
    e.setExpiresAt(d.expiresAt());
    return e;
  }
}
