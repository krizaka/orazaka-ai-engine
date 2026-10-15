package com.krizaka.orazaka.core.infrastructure.provider.model;

/**
 * The provider bound to a model cannot be reached — for a local model (Ollama, LocalAI behind the
 * GPU relay) it means no worker is connected. Says which model and which provider, so the turn
 * fails with a reason instead of a bare "Connection refused".
 */
public class ModelUnavailableException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * @param model the model the turn asked for
   * @param provider where the catalogue binds it (provider name and base URL)
   * @param cause the connection failure
   */
  public ModelUnavailableException(String model, String provider, Throwable cause) {
    super(
        "Model '"
            + model
            + "' is served by "
            + provider
            + ", which is not reachable: no worker is connected for it (start a GPU host or the"
            + " local worker, or choose a hosted model).",
        cause);
  }
}
