package com.krizaka.orazaka.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.entity.LawImportEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The imports of the law corpus. */
public interface LawImportRepository extends JpaRepository<LawImportEntity, UUID> {

  /**
   * The newest import of a dataset with a given status and mode.
   *
   * @param sourceKey the source
   * @param datasetUrl the dataset address
   * @param mode {@code AUTOMATED} or {@code MANUAL}
   * @param status {@code SUCCEEDED}, for the last good read
   * @return the import
   */
  Optional<LawImportEntity> findFirstBySourceKeyAndDatasetUrlAndModeAndStatusOrderByStartedAtDesc(
      String sourceKey, String datasetUrl, String mode, String status);
}
