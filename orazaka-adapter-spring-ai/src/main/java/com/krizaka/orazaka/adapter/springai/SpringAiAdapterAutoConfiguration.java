package com.krizaka.orazaka.adapter.springai;

import com.krizaka.orazaka.adapter.springai.structured.SpringAiStructuredOutputClient;
import com.krizaka.orazaka.adapter.springai.tool.SpringAiToolOptions;
import com.krizaka.orazaka.adapter.springai.tool.SpringAiToolRegistry;
import com.krizaka.orazaka.core.domain.ports.outbound.AccessAudit;
import com.krizaka.orazaka.core.domain.ports.outbound.PlatformToolConfigProvider;
import com.krizaka.orazaka.core.domain.ports.outbound.StructuredOutputClient;
import com.krizaka.orazaka.core.domain.ports.outbound.ToolOptions;
import com.krizaka.orazaka.core.domain.ports.outbound.ToolPermissionProvider;
import com.krizaka.orazaka.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.orazaka.core.domain.ports.outbound.ToolResultCache;
import com.krizaka.orazaka.core.infrastructure.adapter.security.LoggingAccessAudit;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.context.annotation.Bean;

/**
 * Spring wiring of the Spring AI adapter (ADR-092 §4): the engine's ports on Spring AI, registered
 * unless the host brings its own.
 */
@AutoConfiguration
public class SpringAiAdapterAutoConfiguration {

  /**
   * The tool registry. Without a {@link ToolResultCache} bean, tool results are not cached.
   *
   * @param configProvider the platform's tool configuration (optional)
   * @param cache where cached results are kept (optional)
   * @param permissions the roles an administrator granted each tool (optional, ADR-091)
   * @return the registry
   */
  @Bean
  @ConditionalOnMissingBean(ToolRegistry.class)
  public SpringAiToolRegistry toolRegistry(
      ObjectProvider<PlatformToolConfigProvider> configProvider,
      ObjectProvider<ToolResultCache> cache,
      ObjectProvider<ToolPermissionProvider> permissions) {
    return new SpringAiToolRegistry(
        configProvider.getIfAvailable(),
        cache.getIfAvailable(NoToolResultCache::new),
        permissions.getIfAvailable());
  }

  /**
   * Where a refused read or tool call is written (ADR-091): a structured log line, unless the host
   * registers its own {@link AccessAudit}.
   *
   * @return the logging audit
   */
  @Bean
  @ConditionalOnMissingBean(AccessAudit.class)
  public LoggingAccessAudit accessAudit() {
    return new LoggingAccessAudit();
  }

  /**
   * The port that offers tools to a provider through its options.
   *
   * @return the adapter
   */
  @Bean
  @ConditionalOnMissingBean(ToolOptions.class)
  public SpringAiToolOptions toolOptions() {
    return new SpringAiToolOptions();
  }

  /**
   * The structured-output port, when exactly one chat model is wired.
   *
   * @param chatModel the model
   * @return the client
   */
  @Bean
  @ConditionalOnSingleCandidate(ChatModel.class)
  @ConditionalOnMissingBean(StructuredOutputClient.class)
  public SpringAiStructuredOutputClient structuredOutputClient(ChatModel chatModel) {
    return new SpringAiStructuredOutputClient(chatModel);
  }

  /** No cache: every call runs the tool. */
  private static final class NoToolResultCache implements ToolResultCache {
    @Override
    public String get(String toolId, String cacheKey) {
      return null;
    }

    @Override
    public void put(String toolId, String cacheKey, String value, long ttlSeconds) {
      // nothing is kept
    }
  }
}
