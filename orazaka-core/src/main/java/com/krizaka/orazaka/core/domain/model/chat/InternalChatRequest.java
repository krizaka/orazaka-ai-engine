package com.krizaka.orazaka.core.domain.model.chat;

import com.krizaka.orazaka.core.domain.model.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Internal engine-level chat request record.
 *
 * <p>This is the <b>engine-internal</b> representation of a chat request, distinct from the
 * public-facing {@link ChatRequest}. The router is responsible for mapping the public request to
 * this internal format before passing it to the engine.
 *
 * <p>Contains the raw prompt text, conversation history, AI options, and user context.
 *
 * @param prompt The current user prompt text.
 * @param messages The conversation history (defensively copied to unmodifiable list).
 * @param options AI-specific configuration options (nullable — uses defaults if null).
 * @param context The execution context carrying user and session metadata.
 * @see InternalChatResponse
 */
public record InternalChatRequest(
    String prompt,
    List<ChatMessage> messages,
    ChatOptions options,
    Context context,
    boolean streaming) {

  /** Compact constructor — defensively copies the message list. */
  public InternalChatRequest {
    messages = (messages == null) ? List.of() : List.copyOf(messages);
  }

  /** Backwards compatible constructor defaulting streaming to false. */
  public InternalChatRequest(
      String prompt, List<ChatMessage> messages, ChatOptions options, Context context) {
    this(prompt, messages, options, context, false);
  }

  /**
   * Compiles the message history and refined prompt into a flat list of Spring AI messages.
   *
   * <p>Maps each {@link ChatMessage} using the provided mapper function, then appends the refined
   * prompt as a final {@link UserMessage}.
   *
   * @param refinedPrompt The refined or original prompt text (nullable — skipped if blank).
   * @param messageMapper A function converting {@link ChatMessage} to Spring AI {@link Message}.
   * @return A mutable list of Spring AI messages ready for prompt assembly.
   */
  public List<Message> compileMessages(
      String refinedPrompt, Function<ChatMessage, Message> messageMapper) {
    List<Message> container = new ArrayList<>();
    if (!this.messages.isEmpty()) {
      container.addAll(this.messages.stream().map(messageMapper).toList());
    }
    if (refinedPrompt != null && !refinedPrompt.isBlank()) {
      container.add(new UserMessage(refinedPrompt));
    }
    return new ArrayList<>(container);
  }

  /**
   * Represents an individual message in the conversation history.
   *
   * @param role The message sender's role ({@code "system"}, {@code "user"}, or {@code
   *     "assistant"}).
   * @param content The text content of the message (defaults to empty string if null).
   */
  public record ChatMessage(String role, String content) {
    /** Compact constructor — validates role and defaults null content to empty string. */
    public ChatMessage {
      if (role == null || role.isBlank()) {
        throw new IllegalArgumentException("Role cannot be empty");
      }
      if (content == null) {
        content = "";
      }
    }
  }

  /**
   * Factory method for creating a simple single-prompt request with no history or context.
   *
   * @param prompt The prompt text.
   * @return A minimal {@link InternalChatRequest} with no messages, options, or context.
   */
  public static InternalChatRequest simple(String prompt) {
    return new InternalChatRequest(prompt, List.of(), null, Context.anonymous());
  }
}
