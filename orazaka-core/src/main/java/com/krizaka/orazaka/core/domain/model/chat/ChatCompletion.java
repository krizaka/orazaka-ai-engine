package com.krizaka.orazaka.core.domain.model.chat;

import java.util.List;

/**
 * What a model answered, as the engines read it: the text, what the provider says it consumed and,
 * in client-executed mode, the tool calls it asked for.
 *
 * @param text the answer (or the chunk of a stream), {@code null} when the provider sent none
 * @param usage the reported usage, {@link TokenUsage#none()} when the provider reported nothing
 * @param toolCalls the calls the model asked for, empty when it asked for none
 */
public record ChatCompletion(String text, TokenUsage usage, List<ToolCall> toolCalls) {

  /** Compact canonical constructor: an absent usage is {@link TokenUsage#none()}. */
  public ChatCompletion {
    usage = usage == null ? TokenUsage.none() : usage;
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }

  /** An answer that asked for no tool. */
  public ChatCompletion(String text, TokenUsage usage) {
    this(text, usage, List.of());
  }
}
