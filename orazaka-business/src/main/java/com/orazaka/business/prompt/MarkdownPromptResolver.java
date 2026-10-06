package com.orazaka.business.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Thread-safe, cached resolver for Git-tracked Markdown prompt templates.
 *
 * <p>Reads prompt files from {@code classpath:prompts/{name}.md} using Spring's {@link
 * ResourceLoader} and caches the resolved content in a {@link ConcurrentHashMap} for lock-free,
 * wait-free subsequent reads.
 *
 * <p><b>Concurrency Contract</b>:
 *
 * <ul>
 *   <li>Readers ({@link #resolve}) perform a single {@code ConcurrentHashMap.get()} — lock-free.
 *   <li>Cache misses trigger a single {@code computeIfAbsent()} — at most one I/O per key per JVM.
 *   <li>{@link #evict} and {@link #evictAll} safely invalidate entries without blocking readers.
 * </ul>
 *
 * <p><b>Graceful Degradation</b>: If a prompt file is missing or unreadable, the resolver logs a
 * warning and returns {@link Optional#empty()} — it never throws unhandled classpath exceptions.
 *
 * @since 1.0.0
 */
public final class MarkdownPromptResolver {

  private static final Logger logger = LoggerFactory.getLogger(MarkdownPromptResolver.class);
  private static final String PROMPT_PREFIX = "classpath:prompts/";
  private static final String PROMPT_SUFFIX = ".md";

  private final ResourceLoader resourceLoader;
  private final ConcurrentHashMap<String, Optional<String>> cache;

  /**
   * Constructs the resolver with the given Spring resource loader.
   *
   * @param resourceLoader Spring's resource loader for classpath resolution.
   */
  public MarkdownPromptResolver(ResourceLoader resourceLoader) {
    if (resourceLoader == null) {
      throw new IllegalArgumentException("ResourceLoader must not be null");
    }
    this.resourceLoader = resourceLoader;
    this.cache = new ConcurrentHashMap<>();
  }

  /**
   * Resolves a named prompt template from the classpath cache.
   *
   * <p>On first access, reads {@code classpath:prompts/{promptName}.md} and caches the result. All
   * subsequent calls return the cached value without I/O.
   *
   * @param promptName The prompt identifier (e.g., "welcome_agent").
   * @return The prompt content, or empty if the file is missing or unreadable.
   */
  public Optional<String> resolve(String promptName) {
    if (promptName == null || promptName.isBlank()) {
      logger.warn("Attempted to resolve a null or blank prompt name.");
      return Optional.empty();
    }
    return cache.computeIfAbsent(promptName, this::loadFromClasspath);
  }

  /**
   * Evicts a single prompt from the cache, forcing a reload on next access.
   *
   * @param promptName The prompt identifier to evict.
   */
  public void evict(String promptName) {
    if (promptName != null) {
      cache.remove(promptName);
      logger.debug("Evicted prompt cache entry: {}", promptName);
    }
  }

  /**
   * Evicts all cached prompts, forcing a full reload on next access.
   *
   * <p>Useful for admin-triggered hot-reload after Git-push of updated prompt files.
   */
  public void evictAll() {
    int size = cache.size();
    cache.clear();
    logger.info("Evicted all {} prompt cache entries.", size);
  }

  /**
   * Returns the current number of cached prompt entries.
   *
   * @return Cache size.
   */
  public int cacheSize() {
    return cache.size();
  }

  /**
   * Loads a prompt file from the classpath.
   *
   * <p>Returns {@link Optional#empty()} if the resource does not exist or cannot be read — graceful
   * degradation over hard failure.
   */
  private Optional<String> loadFromClasspath(String promptName) {
    String location = PROMPT_PREFIX + promptName + PROMPT_SUFFIX;
    Resource resource = resourceLoader.getResource(location);

    if (!resource.exists()) {
      logger.warn("Prompt file not found: {} — returning empty.", location);
      return Optional.empty();
    }

    try (InputStream is = resource.getInputStream()) {
      String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      logger.debug("Loaded prompt '{}' ({} chars) from classpath.", promptName, content.length());
      return Optional.of(content);
    } catch (IOException e) {
      logger.warn("Failed to read prompt file: {} — returning empty.", location, e);
      return Optional.empty();
    }
  }
}
