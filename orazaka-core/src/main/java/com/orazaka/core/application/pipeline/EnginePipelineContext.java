package com.orazaka.core.application.pipeline;

import java.util.List;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Immutable compiled context snapshot produced by the {@link EnginePipelineBridge}.
 *
 * <p>Captures the results of prompt refinement, provider routing, conversation tracking, and the
 * final assembled {@link Prompt} ready for model invocation.
 *
 * @param provider The resolved AI provider name (e.g., {@code "ollama"}).
 * @param conversationId The conversation thread ID extracted from the request context.
 * @param promptText The refined or original prompt text string.
 * @param toPrompt The fully assembled Spring AI {@link Prompt} with messages and options.
 * @param closeables The list of closeable client connections that must be cleaned up after
 *     execution.
 * @param holdId The credit reservation the entitlement gate took for this turn, or {@code null}
 *     when it was not metered. Carried through compilation because the gate runs in the pipeline
 *     while settlement happens after inference — this is what joins the two halves (ADR-033 §6.2).
 */
public record EnginePipelineContext(
    String provider,
    String conversationId,
    String promptText,
    Prompt toPrompt,
    List<AutoCloseable> closeables,
    String holdId) {

  public EnginePipelineContext(
      String provider, String conversationId, String promptText, Prompt toPrompt) {
    this(provider, conversationId, promptText, toPrompt, List.of(), null);
  }

  public EnginePipelineContext(
      String provider,
      String conversationId,
      String promptText,
      Prompt toPrompt,
      List<AutoCloseable> closeables) {
    this(provider, conversationId, promptText, toPrompt, closeables, null);
  }

  public EnginePipelineContext {
    closeables = closeables == null ? List.of() : List.copyOf(closeables);
  }
}
