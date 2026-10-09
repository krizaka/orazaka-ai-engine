package com.krizaka.orazaka.interceptor.enrichment;

import com.krizaka.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.orazaka.core.domain.model.Context;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import com.krizaka.orazaka.core.domain.ports.outbound.MemoryResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Conversation memory interceptor — injects historical messages before inference and persists new
 * messages after completion.
 *
 * <p>Uses {@link MemoryResolver} to obtain a per-conversation {@link ChatMemory} store, enabling
 * multi-turn context within a single conversation thread.
 *
 * @see MemoryResolver
 */
class MemoryInterceptor implements PromptContextInterceptor {
  private final MemoryResolver memoryResolver;

  MemoryInterceptor(MemoryResolver memoryResolver) {
    this.memoryResolver = Objects.requireNonNull(memoryResolver, "MemoryResolver must not be null");
  }

  @Override
  public ChatOptions preProcess(
      InternalChatRequest request, String promptText, List<Message> messages, ChatOptions options) {
    resolveConversationId(request)
        .ifPresent(
            convId -> {
              List<Message> history = memoryResolver.resolve(convId).get(convId);
              if (!history.isEmpty()) messages.addAll(0, history);
            });
    return options;
  }

  @Override
  public void postProcess(InternalChatRequest request, String promptText, String responseText) {
    resolveConversationId(request)
        .ifPresent(
            convId -> {
              ChatMemory chatMemory = memoryResolver.resolve(convId);
              List<Message> newMessages = new ArrayList<>();
              if (promptText != null && !promptText.isBlank())
                newMessages.add(new UserMessage(promptText));
              newMessages.add(new AssistantMessage(responseText));
              chatMemory.add(convId, newMessages);
            });
  }

  private Optional<String> resolveConversationId(InternalChatRequest request) {
    return Optional.ofNullable(request.context())
        .map(Context::conversationId)
        .filter(id -> !id.isBlank());
  }
}
