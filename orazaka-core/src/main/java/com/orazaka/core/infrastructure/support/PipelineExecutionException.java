package com.orazaka.core.infrastructure.support;

/**
 * Exception thrown when the AI execution pipeline fails to process a request or resolve schemas.
 */
public class PipelineExecutionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new exception with the specified message and cause.
   *
   * @param message Detailed message.
   * @param cause Underlying exception trigger.
   */
  public PipelineExecutionException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new exception with the specified message.
   *
   * @param message Detailed message.
   */
  public PipelineExecutionException(String message) {
    super(message);
  }
}
