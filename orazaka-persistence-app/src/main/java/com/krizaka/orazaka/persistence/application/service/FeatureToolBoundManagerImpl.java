package com.krizaka.orazaka.persistence.application.service;

import com.krizaka.orazaka.persistence.domain.ports.inbound.FeatureToolBoundManager;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.entity.FeatureToolBoundEntity;
import com.krizaka.orazaka.persistence.infrastructure.adapter.persistence.repository.FeatureToolBoundRepository;
import java.util.Objects;
import java.util.OptionalInt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private implementation of the {@link FeatureToolBoundManager} inbound port. Not cached:
 * the bound is edited by an administrator and the next turn must see it.
 */
@Service
@Transactional(readOnly = true)
class FeatureToolBoundManagerImpl implements FeatureToolBoundManager {

  private final FeatureToolBoundRepository repository;

  FeatureToolBoundManagerImpl(FeatureToolBoundRepository repository) {
    this.repository =
        Objects.requireNonNull(repository, "FeatureToolBoundRepository cannot be null");
  }

  @Override
  public OptionalInt maxToolRounds(String featureKey) {
    if (featureKey != null && !featureKey.isBlank()) {
      var own = repository.findById(featureKey);
      if (own.isPresent()) {
        return OptionalInt.of(own.get().getMaxToolRounds());
      }
    }
    return repository
        .findById(DEFAULT_FEATURE_KEY)
        .map(FeatureToolBoundEntity::getMaxToolRounds)
        .map(OptionalInt::of)
        .orElseGet(OptionalInt::empty);
  }
}
