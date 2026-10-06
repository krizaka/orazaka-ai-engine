package com.orazaka.core.application.processing;

import com.orazaka.core.domain.model.IntentMesh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Parses raw text input into a multidimensional intent matrix using SIM (Semantic Intent Mesh). */
public class IntentClassifier {

  private static final Logger logger = LoggerFactory.getLogger(IntentClassifier.class);

  /**
   * Evaluates text inputs into a multi-dimensional weight vector, splitting hybrid inputs.
   *
   * @param input Raw user query.
   * @return A self-validating {@link IntentMesh} weights representation.
   */
  public IntentMesh classify(String input) {
    if (input == null || input.isBlank()) {
      return new IntentMesh(0.0, 0.0, 1.0);
    }

    String lower = input.toLowerCase();
    double codeWeight = 0.0;
    double mediaWeight = 0.0;
    double chatWeight = 0.1;

    if (lower.contains("create")
        || lower.contains("scaffold")
        || lower.contains("nextjs")
        || lower.contains("react")
        || lower.contains("code")
        || lower.contains("page")) {
      codeWeight = 0.95;
    }

    if (lower.contains("video")
        || lower.contains("image")
        || lower.contains("media")
        || lower.contains("speech")
        || lower.contains("audio")
        || lower.contains("inference")) {
      mediaWeight = 0.90;
    }

    if (codeWeight > 0.0 && mediaWeight > 0.0) {
      logger.info(
          "[ORAZAKA-SIM-MESH] Intent split detected: input='{}', codeWeight={}, mediaWeight={}",
          input,
          codeWeight,
          mediaWeight);
    } else {
      logger.info(
          "[ORAZAKA-SIM-MESH] Single intent resolved: input='{}', codeWeight={}, mediaWeight={}",
          input,
          codeWeight,
          mediaWeight);
    }

    if (codeWeight == 0.0 && mediaWeight == 0.0) {
      chatWeight = 1.0;
    }

    return new IntentMesh(codeWeight, mediaWeight, chatWeight);
  }
}
