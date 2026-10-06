package com.orazaka.core.application.interceptor;

import com.orazaka.core.domain.model.PromptContext;
import com.orazaka.core.domain.model.chat.InternalChatRequest;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Core port interface for the prompt and context orchestration pipeline. Unifies PromptInterceptor
 * and ContextInterceptor capabilities.
 *
 * <p>Implementations are loaded dynamically via Spring Boot auto-configuration from external
 * interceptor submodules ({@code orazaka-interceptor-*}).
 *
 * <p>Interceptors that require AI model calls must override {@link #isAiDependent()} to return
 * {@code true}. When the governance kill-switch ({@code orazaka.security.disable-ai=true}) is
 * active, the {@code DynamicPipelineExecutor} will throw a hard {@link SecurityException} if any
 * AI-dependent interceptor is invoked.
 *
 * <p><strong>Lifecycle contract</strong>: The pipeline executor invokes interceptors using the
 * two-phase lifecycle:
 *
 * <ol>
 *   <li>{@link #beforeExecution(PromptContext)} — Pre-inference enrichment (context building,
 *       metadata injection). Defaults to {@link #intercept(PromptContext)} for backward
 *       compatibility.
 *   <li>{@link #afterExecution(PromptContext, String)} — Post-inference processing (validation,
 *       logging, metrics). Defaults to no-op.
 * </ol>
 *
 * <p><strong>Ordering</strong>: Interceptors have zero compiled-in knowledge of their execution
 * position. Sequence is resolved dynamically by the {@code DynamicPipelineExecutor} from the
 * database-driven {@code PipelineRegistry}. Hardcoded ordering methods are prohibited.
 */
public interface PromptContextInterceptor {

  /**
   * Returns a stable identifier for this interceptor instance. Used by the pipeline registry for
   * bean resolution and logging.
   *
   * @return The interceptor identifier, defaulting to the simple class name.
   */
  default String getId() {
    return getClass().getSimpleName();
  }

  /**
   * Intercepts and enriches the PromptContext.
   *
   * @param context The current prompt context state.
   * @return The updated prompt context state.
   * @deprecated Use {@link #beforeExecution(PromptContext)} instead. Retained for backward
   *     compatibility — the default {@code beforeExecution} delegates to this method.
   */
  @Deprecated(since = "2026.2.0", forRemoval = false)
  default PromptContext intercept(PromptContext context) {
    return context;
  }

  /**
   * Pre-inference lifecycle hook. Invoked before model execution to enrich, transform, or gate the
   * prompt context.
   *
   * <p>Default implementation delegates to {@link #intercept(PromptContext)} for backward
   * compatibility with existing interceptor implementations.
   *
   * @param context The current prompt context state.
   * @return The updated prompt context state.
   */
  @SuppressWarnings("deprecation")
  default PromptContext beforeExecution(PromptContext context) {
    return intercept(context);
  }

  /**
   * Post-inference lifecycle hook. Invoked after model execution completes to perform validation,
   * logging, metrics collection, or response transformation.
   *
   * @param context The prompt context that was sent to the model.
   * @param responseText The text response returned by the model (may be null if inference failed).
   */
  default void afterExecution(PromptContext context, String responseText) {
    // Default no-op — override for post-inference processing
  }

  /**
   * Indicates whether this interceptor depends on AI model inference calls. Interceptors returning
   * {@code true} will be blocked by the security governance kill-switch when {@code
   * orazaka.security.disable-ai=true} is set.
   *
   * @return {@code true} if this interceptor requires AI model calls, {@code false} otherwise.
   */
  default boolean isAiDependent() {
    return false;
  }

  /**
   * Whether a failure this interceptor did not handle stops the turn (ADR-064).
   *
   * <p><b>Declared, not left to a {@code catch} block.</b> The pipeline used to swallow every
   * exception but a deliberate refusal, which is right for an enrichment — a broken RAG lookup
   * should degrade the answer, not lose the user's turn — and made every control fail open by
   * accident: a crisis guard that threw on a malformed declaration passed the turn, and a billing
   * gate that could not reach billing served it free, and nobody had chosen either. A control
   * declares {@code true}, and handles inside itself the failures it has decided to survive;
   * whatever it did not anticipate then stops the turn instead of passing it.
   *
   * @return {@code true} for a control, whose unhandled failure must stop the turn; {@code false}
   *     (the default) for an enrichment, whose failure degrades it
   */
  default boolean failsClosed() {
    return false;
  }

  /**
   * Enriches request messages or ChatOptions before sending to the Spring AI models.
   *
   * @param request The original Orazaka chat request.
   * @param promptText The current (possibly refined) prompt text.
   * @param messages The accumulator list of mapped messages.
   * @param options The current Spring AI chat options.
   * @return The updated ChatOptions (must not return null; should return the passed options if
   *     unchanged).
   */
  default ChatOptions preProcess(
      InternalChatRequest request, String promptText, List<Message> messages, ChatOptions options) {
    return options;
  }

  /**
   * Invoked after the model execution completes successfully.
   *
   * @param request The original Orazaka chat request.
   * @param promptText The (possibly refined) prompt text that was sent.
   * @param responseText The text response returned by the model.
   */
  default void postProcess(InternalChatRequest request, String promptText, String responseText) {}
}
