package com.krizaka.orazaka.core.domain.model.embedding;

/**
 * An embedding could not be produced: no embedding model is configured, the provider refused or did
 * not answer, or its answer was malformed. Never carries a provider key or an input text.
 */
public class EmbeddingException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public EmbeddingException(String message) {
    super(message);
  }

  public EmbeddingException(String message, Throwable cause) {
    super(message, cause);
  }
}
