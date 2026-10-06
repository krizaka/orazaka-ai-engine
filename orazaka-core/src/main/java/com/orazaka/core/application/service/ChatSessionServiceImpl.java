package com.orazaka.core.application.service;

import com.orazaka.core.domain.model.chat.ChatSessionInfo;
import com.orazaka.core.domain.ports.inbound.ChatSessionService;
import com.orazaka.persistence.domain.model.ChatSessionDto;
import com.orazaka.persistence.domain.ports.inbound.ChatSessionPersistenceProvider;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Package-private implementation of the ChatSessionService inbound port. Delegates to persistence.
 * Follows ERR-105.
 */
@Service
class ChatSessionServiceImpl implements ChatSessionService {

  private final ChatSessionPersistenceProvider provider;

  ChatSessionServiceImpl(ChatSessionPersistenceProvider provider) {
    this.provider =
        Objects.requireNonNull(provider, "ChatSessionPersistenceProvider cannot be null");
  }

  @Override
  public ChatSessionInfo save(ChatSessionInfo session) {
    return toInfo(provider.save(toDto(session)));
  }

  @Override
  public Optional<ChatSessionInfo> getSession(String id) {
    return provider.findById(id).map(ChatSessionServiceImpl::toInfo);
  }

  @Override
  public List<ChatSessionInfo> getSessionsByUserId(String userId) {
    return provider.findAllByUserId(userId).stream().map(ChatSessionServiceImpl::toInfo).toList();
  }

  @Override
  public void deleteSession(String id) {
    provider.deleteById(id);
  }

  @Override
  public void purgeSessionsByUserId(String userId) {
    provider.purgeByUserId(userId);
  }

  private static ChatSessionInfo toInfo(ChatSessionDto dto) {
    return new ChatSessionInfo(dto.id(), dto.userId(), dto.title(), dto.updatedAt());
  }

  private static ChatSessionDto toDto(ChatSessionInfo info) {
    return new ChatSessionDto(info.id(), info.userId(), info.title(), info.updatedAt());
  }
}
