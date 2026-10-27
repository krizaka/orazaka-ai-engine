package com.krizaka.orazaka.core.application.service;

import com.krizaka.orazaka.core.domain.model.chat.EngineMessage;
import com.krizaka.orazaka.core.domain.ports.outbound.ChatMemoryStore;
import com.krizaka.orazaka.persistence.domain.model.ChatMessageDto;
import com.krizaka.orazaka.persistence.domain.ports.inbound.ChatMemoryPersistenceProvider;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

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

  private static final JsonMapper TOOL_JSON = new JsonMapper();

  private final ChatMemoryPersistenceProvider provider;

  ChatMemoryStoreImpl(ChatMemoryPersistenceProvider provider) {
    this.provider =
        Objects.requireNonNull(provider, "ChatMemoryPersistenceProvider cannot be null");
  }

  @Override
  public List<EngineMessage> findRecent(String conversationId, int limit) {
    try {
      return provider.findRecent(conversationId, limit).stream()
          .map(ChatMemoryStoreImpl::toMessage)
          .toList();
    } catch (DataAccessException e) {
      logger.warn(
          "Conversation memory unavailable (read) for {} — continuing without history",
          conversationId,
          e);
      return List.of();
    }
  }

  @Override
  public void append(String conversationId, List<EngineMessage> messages) {
    try {
      provider.appendAll(
          messages.stream()
              .map(m -> new ChatMessageDto(conversationId, roleOf(m), contentOf(m), null))
              .toList());
    } catch (DataAccessException e) {
      logger.warn(
          "Conversation memory unavailable (write) for {} — turn not persisted", conversationId, e);
    }
  }

  @Override
  public void clear(String conversationId) {
    try {
      provider.deleteByConversationId(conversationId);
    } catch (DataAccessException e) {
      logger.warn("Conversation memory clear failed for {}", conversationId, e);
    }
  }

  private static EngineMessage toMessage(ChatMessageDto dto) {
    return switch (dto.role().toLowerCase(Locale.ROOT)) {
      case "assistant" -> EngineMessage.assistant(dto.content());
      case "system" -> EngineMessage.system(dto.content());
      case "tool" -> {
        ToolResponseMessage tool = toolMessage(dto.content());
        yield new EngineMessage(EngineMessage.Role.TOOL, tool.getText(), tool);
      }
      default -> EngineMessage.user(dto.content());
    };
  }

  /** The role as stored: the message's own, so a tool answer stays a tool answer. */
  private static String roleOf(EngineMessage message) {
    return message.role().name().toLowerCase(Locale.ROOT);
  }

  private static String contentOf(EngineMessage message) {
    if (message.nativeMessage() instanceof ToolResponseMessage tool) {
      return TOOL_JSON.writeValueAsString(
          tool.getResponses().stream()
              .map(r -> new StoredToolResponse(r.id(), r.name(), r.responseData()))
              .toList());
    }
    return message.text();
  }

  private static ToolResponseMessage toolMessage(String stored) {
    List<StoredToolResponse> responses;
    try {
      responses = TOOL_JSON.readValue(stored, new tools.jackson.core.type.TypeReference<>() {});
    } catch (RuntimeException unreadable) {
      // Written before tool answers were structured: keep the text as the one answer.
      responses = List.of(new StoredToolResponse("", "", stored));
    }
    return ToolResponseMessage.builder()
        .responses(
            responses.stream()
                .map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(), r.responseData()))
                .toList())
        .build();
  }

  /** One tool answer as stored in the message content. */
  private record StoredToolResponse(String id, String name, String responseData) {}
}
