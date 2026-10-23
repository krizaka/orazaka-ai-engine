package com.krizaka.orazaka.tools.infrastructure.adapter.persistence.repository;

import com.krizaka.orazaka.tools.infrastructure.adapter.persistence.entity.ResearchFetchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** The research fetch audit. */
public interface ResearchFetchRepository extends JpaRepository<ResearchFetchEntity, Long> {}
