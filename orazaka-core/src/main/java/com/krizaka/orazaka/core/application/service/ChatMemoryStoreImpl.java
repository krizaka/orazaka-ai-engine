package com.krizaka.orazaka.core.application.service;

import com.krizaka.orazaka.core.domain.ports.outbound.ChatMemoryStore;
import com.krizaka.orazaka.persistence.domain.model.ChatMessageDto;
import com.krizaka.orazaka.persistence.domain.ports.inbound.ChatMemoryPersistenceProvider;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Package-private adapter binding the core {@link ChatMemoryStore} port to the persistence module's
 * {@link ChatMemoryPersistenceProvider} (Postgres). Mirrors ChatSessionServiceImpl. Follows
 * ERR-105.
 *
 * <p>Conversation memory is best-effort enrichment: a persistence failure (e.g. the table not yet
 * provisioned, or a transient DB error) must never break the chat response. Each operation
 * therefore degrades gracefully — reads return no history, writes are skipped — and logs a warning.
 */
@Service
class ChatMemoryStoreImpl implements ChatMemoryStore {

  private static final Logger logger = LoggerFactory.getLogger(ChatMemoryStoreImpl.class);

  private final ChatMemoryPersistenceProvider provider;

  ChatMemoryStoreImpl(ChatMemoryPersistenceProvider provider) {
    this.provider =
        Objects.requireNonNull(provider, "ChatMemoryPersistenceProvider cannot be null");
  }

  @Override
  public List<Message> findRecent(String conversationId, int limit) {
    try {
      return provider.findRecent(conversationId, limit).stream()
          .map(ChatMemoryStoreImpl::toMessage)
          .toList();
    } catch (DataAccessException e) {
      logger.warn(
          "Conversation memory unavailable (read) for {} — continuing without history: {}",
          conversationId,
          e.getMessage());
      return List.of();
    }
  }

  @Override
  public void append(String conversationId, List<Message> messages) {
    try {
      provider.appendAll(
          messages.stream()
              .map(m -> new ChatMessageDto(conversationId, roleOf(m), m.getText(), null))
              .toList());
    } catch (DataAccessException e) {
      logger.warn(
          "Conversation memory unavailable (write) for {} — turn not persisted: {}",
          conversationId,
          e.getMessage());
    }
  }

  @Override
  public void clear(String conversationId) {
    try {
      provider.deleteByConversationId(conversationId);
    } catch (DataAccessException e) {
      logger.warn("Conversation memory clear failed for {}: {}", conversationId, e.getMessage());
    }
  }

  private static Message toMessage(ChatMessageDto dto) {
    return switch (dto.role()) {
      case "assistant" -> new AssistantMessage(dto.content());
      case "system" -> new SystemMessage(dto.content());
      default -> new UserMessage(dto.content());
    };
  }

  private static String roleOf(Message message) {
    if (message instanceof AssistantMessage) {
      return "assistant";
    }
    if (message instanceof SystemMessage) {
      return "system";
    }
    return "user";
  }
}
