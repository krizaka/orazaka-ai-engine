package com.krizaka.orazaka.core.domain.model.chat;

/**
 * What a model answered, as the engines read it: the text, and what the provider says it consumed.
 *
 * @param text the answer (or the chunk of a stream), {@code null} when the provider sent none
 * @param usage the reported usage, {@link TokenUsage#none()} when the provider reported nothing
 */
public record ChatCompletion(String text, TokenUsage usage) {

  /** Compact canonical constructor: an absent usage is {@link TokenUsage#none()}. */
  public ChatCompletion {
    usage = usage == null ? TokenUsage.none() : usage;
  }
}
