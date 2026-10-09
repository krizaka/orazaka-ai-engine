package com.orazaka.core.infrastructure.provider;

import com.orazaka.core.domain.model.ModelEndpoint;
import com.orazaka.core.domain.ports.outbound.ModelEndpointResolver;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * Dynamic Model Router (ADR-030).
 *
 * <p>Resolves a {@link ChatModel} <b>on demand from the DB</b>, replacing the static
 * {@code @Primary} model beans in the former {@code AiModelConfiguration}. Given a model name (the
 * user's {@code user_model_prefs} choice, or a category's catalog default), it resolves the
 * provider endpoint via {@link ModelEndpointResolver} and builds an OpenAI-compatible client via
 * the {@link UniversalProxyChatProvider}. No Spring Boot starter / autoconfiguration is involved.
 *
 * <p>Built clients are cached per {@code (base-url, model)}. Because providers/models live in the
 * DB, an admin edit takes effect with no redeploy — call {@link #evictAll()} after such an edit (or
 * wire a short TTL).
 *
 * @see UniversalProxyChatProvider
 * @see ModelEndpointResolver
 */
@Component
public class DynamicModelRouter {

  private final ModelEndpointResolver resolver;
  private final UniversalProxyChatProvider proxy;
  private final Map<String, ChatModel> cache = new ConcurrentHashMap<>();

  public DynamicModelRouter(ModelEndpointResolver resolver, UniversalProxyChatProvider proxy) {
    this.resolver = Objects.requireNonNull(resolver, "ModelEndpointResolver cannot be null");
    this.proxy = Objects.requireNonNull(proxy, "UniversalProxyChatProvider cannot be null");
  }

  /** Builds (or returns a cached) {@link ChatModel} for the given model name. */
  public ChatModel chatModel(String modelName) {
    return chatModelFor(
        resolver
            .resolve(modelName)
            .orElseThrow(
                () -> new IllegalStateException("No endpoint resolved for model: " + modelName)));
  }

  /** Builds (or returns a cached) {@link ChatModel} for a category's default model. */
  public ChatModel defaultChatModel(String category) {
    return chatModelFor(
        resolver
            .resolveDefault(category)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No default model endpoint resolved for category: " + category)));
  }

  /** Clears the client cache — call after an admin edits providers/models in the DB. */
  public void evictAll() {
    cache.clear();
  }

  private ChatModel chatModelFor(ModelEndpoint endpoint) {
    String key = endpoint.baseUrl() + "::" + endpoint.modelName();
    return cache.computeIfAbsent(
        key,
        ignored -> proxy.chatModel(endpoint.baseUrl(), endpoint.apiKey(), endpoint.modelName()));
  }
}
