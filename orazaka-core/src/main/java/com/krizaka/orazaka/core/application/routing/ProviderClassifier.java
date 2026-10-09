package com.krizaka.orazaka.core.application.routing;

import java.util.Set;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * Centralized provider classification utility.
 *
 * <p>Eliminates scattered {@code "openai".equalsIgnoreCase(provider)} checks across the codebase by
 * consolidating all provider-related logic into a single source of truth.
 */
public final class ProviderClassifier {

  /** Provider name constant for Ollama. */
  private static final String OLLAMA_PROVIDER = "ollama";

  /** Provider name constant for OpenAI. */
  private static final String OPENAI_PROVIDER = "openai";

  /** Provider name constant for Anthropic. */
  private static final String ANTHROPIC_PROVIDER = "anthropic";

  /** Provider name constant for Gemini. */
  private static final String GEMINI_PROVIDER = "gemini";

  /** Provider name constant for LocalAI. */
  private static final String LOCALAI_PROVIDER = "localai";

  /**
   * Set of commercial (cloud-hosted, API-key-required) provider identifiers. Checked
   * case-insensitively.
   */
  private static final Set<String> COMMERCIAL_PROVIDERS =
      Set.of(OPENAI_PROVIDER, "claude", ANTHROPIC_PROVIDER, GEMINI_PROVIDER, "google");

  /** Set of local (self-hosted, no API key required) provider identifiers. */
  private static final Set<String> LOCAL_PROVIDERS = Set.of(OLLAMA_PROVIDER, LOCALAI_PROVIDER);

  private ProviderClassifier() {}

  /**
   * @return The Ollama provider identifier.
   */
  public static String ollama() {
    return OLLAMA_PROVIDER;
  }

  /**
   * @return The OpenAI provider identifier.
   */
  public static String openai() {
    return OPENAI_PROVIDER;
  }

  /**
   * @return The Anthropic provider identifier.
   */
  public static String anthropic() {
    return ANTHROPIC_PROVIDER;
  }

  /**
   * @return The Gemini provider identifier.
   */
  public static String gemini() {
    return GEMINI_PROVIDER;
  }

  /**
   * Returns {@code true} if the given provider requires a commercial API key.
   *
   * @param provider The provider identifier (e.g. "openai", "anthropic").
   * @return {@code true} for cloud-hosted providers, {@code false} for local providers.
   */
  public static boolean isCommercial(String provider) {
    return provider != null && COMMERCIAL_PROVIDERS.contains(provider.toLowerCase());
  }

  /**
   * Returns {@code true} if the given provider is a local self-hosted engine.
   *
   * @param provider The provider identifier.
   * @return {@code true} for local providers like Ollama.
   */
  public static boolean isLocal(String provider) {
    return provider != null && LOCAL_PROVIDERS.contains(provider.toLowerCase());
  }

  /**
   * Resolves a provider name from Spring AI {@link ChatOptions} type.
   *
   * @param options The chat options instance (nullable).
   * @return The inferred provider name, or {@code null} if unresolvable.
   */
  public static String resolveFromOptions(ChatOptions options) {
    return switch (options) {
      case OllamaChatOptions ignored -> OLLAMA_PROVIDER;
      case OpenAiChatOptions ignored -> OPENAI_PROVIDER;
      case AnthropicChatOptions ignored -> ANTHROPIC_PROVIDER;
      case GoogleGenAiChatOptions ignored -> GEMINI_PROVIDER;
      case null, default -> null;
    };
  }

  /**
   * Extracts the model name from a Spring AI {@link ChatOptions} instance.
   *
   * <p>Centralizes the scattered {@code instanceof} checks that were previously duplicated in
   * {@code AbstractEngine} and {@code EnginePipelineBridge}. Uses Java 21 exhaustive pattern
   * matching to ensure compile-time safety.
   *
   * @param options The chat options instance (nullable).
   * @return The model name, or {@code null} if the options type is unrecognized or null.
   */
  public static String resolveModelName(ChatOptions options) {
    return switch (options) {
      case OpenAiChatOptions o -> o.getModel();
      case AnthropicChatOptions a -> a.getModel();
      case GoogleGenAiChatOptions g -> g.getModel();
      case OllamaChatOptions ol -> ol.getModel();
      case null, default -> null;
    };
  }
}
