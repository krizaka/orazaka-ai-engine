package com.orazaka.persistence.infrastructure.adapter.persistence.repository;

import com.orazaka.persistence.infrastructure.adapter.persistence.entity.ProcessedMessageEntity;
import com.orazaka.persistence.infrastructure.adapter.persistence.entity.ProcessedMessageKey;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link ProcessedMessageEntity}. */
@Repository
public interface ProcessedMessageRepository
    extends JpaRepository<ProcessedMessageEntity, ProcessedMessageKey> {

  long deleteByProcessedAtBefore(Instant cutoff);
}
