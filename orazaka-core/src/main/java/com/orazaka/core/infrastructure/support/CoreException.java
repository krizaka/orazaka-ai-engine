package com.orazaka.core.infrastructure.support;

/**
 * Base exception for the Orazaka core module. Decouples the client from underlying Spring AI
 * exceptions.
 */
public class CoreException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new exception with the specified message.
   *
   * @param message The detail message.
   */
  public CoreException(String message) {
    super(message);
  }

  /**
   * Constructs a new exception with message and cause.
   *
   * @param message The detail message.
   * @param cause The underlying cause.
   */
  public CoreException(String message, Throwable cause) {
    super(message, cause);
  }
}
