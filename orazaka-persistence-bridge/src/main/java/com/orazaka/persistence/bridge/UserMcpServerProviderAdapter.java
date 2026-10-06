package com.orazaka.persistence.bridge;

import com.orazaka.core.domain.ports.outbound.UserMcpServerProvider;
import com.orazaka.persistence.domain.ports.inbound.UserMcpServerPersistenceProvider;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Implementation of the outbound UserMcpServerProvider port using the identity persistence
 * provider.
 */
@Service
class UserMcpServerProviderAdapter implements UserMcpServerProvider {

  private final UserMcpServerPersistenceProvider persistenceProvider;

  UserMcpServerProviderAdapter(UserMcpServerPersistenceProvider persistenceProvider) {
    this.persistenceProvider =
        Objects.requireNonNull(persistenceProvider, "persistenceProvider must not be null");
  }

  @Override
  public List<UserMcpServer> getActiveUserMcpServers(String userId) {
    if (userId == null) {
      return List.of();
    }
    return persistenceProvider.findByUserIdAndEnabledTrue(userId).stream()
        .map(
            dto ->
                new UserMcpServer(
                    dto.id(), dto.userId(), dto.label(), dto.url(), dto.authToken(), dto.enabled()))
        .toList();
  }
}
