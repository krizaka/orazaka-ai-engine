package com.orazaka.core.domain.model;

import java.util.List;

/**
 * Stateful DAG representation for dynamic intent execution, tracking offload/queue states.
 *
 * @param tasks List of compiled task IDs.
 * @param dependencies List of dependency edge declarations.
 * @param offloaded Flag indicating if branch rerouting was applied.
 * @param throttled Flag indicating if backpressure throttling was applied.
 */
public record AdaptiveDag(
    List<String> tasks, List<String> dependencies, boolean offloaded, boolean throttled) {

  /** Compact constructor with defensive copies per §2.4. */
  public AdaptiveDag {
    tasks = tasks != null ? List.copyOf(tasks) : List.of();
    dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
  }
}
