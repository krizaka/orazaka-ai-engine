package com.orazaka.core.domain.model;

/**
 * Self-validating record representing multidimensional intent weights parsed by the Semantic Intent
 * Mesh.
 */
public record IntentMesh(
    double codeGenerationWeight, double mediaInferenceWeight, double generalChatWeight) {
  public IntentMesh {
    if (codeGenerationWeight < 0.0 || codeGenerationWeight > 1.0) {
      throw new IllegalArgumentException("codeGenerationWeight must be between 0.0 and 1.0");
    }
    if (mediaInferenceWeight < 0.0 || mediaInferenceWeight > 1.0) {
      throw new IllegalArgumentException("mediaInferenceWeight must be between 0.0 and 1.0");
    }
    if (generalChatWeight < 0.0 || generalChatWeight > 1.0) {
      throw new IllegalArgumentException("generalChatWeight must be between 0.0 and 1.0");
    }
  }
}
