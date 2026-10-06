package com.orazaka.core.application.processing;

import com.orazaka.core.domain.model.AdaptiveDag;
import com.orazaka.core.domain.model.IntentMesh;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateful compiler constructing AdaptiveDag execution graphs and evaluating system memory
 * telemetry to implement the Self-Healing Hardware Loop.
 */
public class DagCompiler {

  private static final Logger logger = LoggerFactory.getLogger(DagCompiler.class);

  private static final String TASK_FEATURE_TO_CODE = "feature-to-code";
  private static final String TASK_LOCAL_MEDIA_GEN = "local-media-generation";

  /**
   * Compiles the dynamic execution graph based on semantic intent weights and current hardware
   * pressure.
   *
   * @param mesh The input IntentMesh containing intent weight dimensions.
   * @param unifiedMemoryUsagePercent Current simulated or measured memory footprint (e.g. 70.0 for
   *     70%).
   * @return A compiled stateful {@link AdaptiveDag}.
   */
  public AdaptiveDag compile(IntentMesh mesh, double unifiedMemoryUsagePercent) {
    List<String> tasks = new ArrayList<>();
    List<String> dependencies = new ArrayList<>();
    boolean offloaded = false;
    boolean throttled = false;

    if (mesh.codeGenerationWeight() > 0.5) {
      tasks.add(TASK_FEATURE_TO_CODE);
    }

    if (mesh.mediaInferenceWeight() > 0.5) {
      tasks.add(TASK_LOCAL_MEDIA_GEN);
    }

    if (tasks.contains(TASK_FEATURE_TO_CODE) && tasks.contains(TASK_LOCAL_MEDIA_GEN)) {
      dependencies.add(TASK_LOCAL_MEDIA_GEN + " -> " + TASK_FEATURE_TO_CODE);
    }

    if (unifiedMemoryUsagePercent > 85.0) {
      logger.info(
          "[ORAZAKA-DAG-ENGINE] Backpressure mitigation triggered: Unified Memory usage threshold exceeded. Offloading task nodes.");
      if (tasks.contains(TASK_FEATURE_TO_CODE)) {
        offloaded = true;
        logger.info(
            "[ORAZAKA-DAG-ENGINE] Rerouting code scaffolding task to commercial cloud API endpoint.");
      }
      if (tasks.contains(TASK_LOCAL_MEDIA_GEN)) {
        throttled = true;
        logger.info(
            "[ORAZAKA-DAG-ENGINE] Throttling local media generation queue. Locking GPU/MPS assets.");
      }
    } else {
      logger.info(
          "[ORAZAKA-DAG-ENGINE] Graph optimization completed: dependencies resolved. Running all tasks locally.");
    }

    return new AdaptiveDag(tasks, dependencies, offloaded, throttled);
  }
}
