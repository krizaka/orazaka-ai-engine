package com.krizaka.orazaka.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.entity.ResearchDocumentEntity;
import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.entity.ResearchDocumentId;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Documents research read, by URL and content hash. */
public interface ResearchDocumentRepository
    extends JpaRepository<ResearchDocumentEntity, ResearchDocumentId> {

  /**
   * The newest document of a URL still fresh at an instant.
   *
   * @param url the URL
   * @param now the instant
   * @return the document
   */
  Optional<ResearchDocumentEntity> findFirstByIdUrlAndExpiresAtAfterOrderByFetchedAtDesc(
      String url, Instant now);
}
