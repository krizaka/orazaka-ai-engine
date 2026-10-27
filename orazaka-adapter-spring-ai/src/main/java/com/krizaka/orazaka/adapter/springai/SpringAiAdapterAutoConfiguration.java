package com.krizaka.orazaka.adapter.springai;

import com.krizaka.orazaka.adapter.springai.chat.SpringAiProviderChatClient;
import com.krizaka.orazaka.adapter.springai.client.ChatGeneratorClientImpl;
import com.krizaka.orazaka.adapter.springai.client.EmbeddingClientImpl;
import com.krizaka.orazaka.adapter.springai.structured.SpringAiStructuredOutputClient;
import com.krizaka.orazaka.adapter.springai.tool.SpringAiToolOptions;
import com.krizaka.orazaka.adapter.springai.tool.SpringAiToolRegistry;
import com.krizaka.orazaka.core.application.engine.Engine;
import com.krizaka.orazaka.core.application.pipeline.PipelineOptionsRegistry;
import com.krizaka.orazaka.core.domain.ports.outbound.*;
import com.krizaka.orazaka.core.infrastructure.adapter.security.LoggingAccessAudit;
import com.krizaka.orazaka.core.infrastructure.spend.SpendGuard;
import java.util.Map;
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

  /**
   * The port that asks the model of a provider, over the provider-keyed {@link ChatModel} beans (an
   * empty set when there are none).
   *
   * @param chatModels the models by provider key
   * @param optionsRegistry builds the provider-specific options
   * @return the client
   */
  @Bean
  @ConditionalOnMissingBean(ProviderChatClient.class)
  public SpringAiProviderChatClient providerChatClient(
      ObjectProvider<Map<String, ChatModel>> chatModels, PipelineOptionsRegistry optionsRegistry) {
    return new SpringAiProviderChatClient(chatModels.getIfAvailable(Map::of), optionsRegistry);
  }

  /**
   * The chat port: a request runs through the engine.
   *
   * @param engine the engine
   * @return the client
   */
  @Bean
  @ConditionalOnMissingBean(ChatGeneratorClient.class)
  public ChatGeneratorClientImpl chatGeneratorClientImpl(Engine engine) {
    return new ChatGeneratorClientImpl(engine);
  }

  /**
   * The embedding port, over any OpenAI-compatible {@code /v1/embeddings} endpoint.
   *
   * @param endpoints where the embedding model lives
   * @param spendGuard the daily budget of paid providers
   * @return the client
   */
  @Bean
  @ConditionalOnMissingBean(EmbeddingClient.class)
  public EmbeddingClientImpl embeddingClientImpl(
      ModelEndpointResolver endpoints, SpendGuard spendGuard) {
    return new EmbeddingClientImpl(endpoints, spendGuard);
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
