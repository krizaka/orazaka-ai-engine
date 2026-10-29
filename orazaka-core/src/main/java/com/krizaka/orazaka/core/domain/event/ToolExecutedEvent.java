package com.krizaka.orazaka.core.domain.event;

import java.util.Objects;

/**
 * Announces that the engine itself ran a tool the model asked for (a server tool; a tool the client
 * runs is returned to it, never announced here). It carries the tool's name and the call id, never
 * its arguments or result (ADR-064), so a turn's tool use can be seen from outside.
 *
 * <p>Published synchronously, in-process, on the request thread, like {@link PipelineStageEvent};
 * correlated downstream by {@code conversationId}.
 *
 * @param conversationId the conversation the turn belongs to
 * @param toolName the tool that ran
 * @param callId the provider's id of the call, {@code null} when it sent none
 */
public record ToolExecutedEvent(String conversationId, String toolName, String callId) {

  /** Compact constructor enforcing non-null invariants. */
  public ToolExecutedEvent {
    Objects.requireNonNull(conversationId, "conversationId must not be null");
    Objects.requireNonNull(toolName, "toolName must not be null");
  }
}
