package com.orazaka.persistence.infrastructure.adapter.persistence.repository;

import com.orazaka.persistence.infrastructure.adapter.persistence.entity.CatalogModelEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** JPA Repository for accessing orazaka_models catalog table. */
@Repository
public interface CatalogModelRepository extends JpaRepository<CatalogModelEntity, Integer> {

  /**
   * Finds all models belonging to a specific category.
   *
   * @param category The category (e.g. speech, image, video, vision).
   * @return A list of CatalogModelEntity.
   */
  List<CatalogModelEntity> findByCategory(String category);

  /**
   * Finds a model by its unique name.
   *
   * @param modelName The model's unique identifier name.
   * @return Optional containing the CatalogModelEntity if found.
   */
  Optional<CatalogModelEntity> findByModelName(String modelName);

  /**
   * Finds the default model belonging to a specific category.
   *
   * @param category The category (e.g. speech, image, video, vision).
   * @return Optional containing the default CatalogModelEntity if found.
   */
  Optional<CatalogModelEntity> findByCategoryAndIsDefaultTrue(String category);
}
