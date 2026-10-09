package com.krizaka.orazaka.tools.infrastructure.adapter.mcp;

import com.krizaka.orazaka.tools.infrastructure.cache.ToolCacheStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * A caching decorator for the Spring AI {@link ToolCallback} SPI.
 *
 * <p>Lives in {@code infrastructure.adapter.mcp} (not {@code application.service}): it implements a
 * framework SPI and is instantiated by {@link DefaultToolRegistry} — infrastructure plumbing, not
 * an application {@code *Service} ([ERR-130]). The caching policy itself lives in {@link
 * ToolCacheStore}.
 */
public class CachingToolCallback implements ToolCallback {

  private static final Logger log = LoggerFactory.getLogger(CachingToolCallback.class);

  private final ToolCallback delegate;
  private final ToolCacheStore cacheService;
  private final boolean cacheEnabled;
  private final long ttlSeconds;

  /**
   * Creates a new CachingToolCallback.
   *
   * @param delegate The actual tool callback to wrap.
   * @param cacheService The cache service to store/retrieve results.
   * @param cacheEnabled Whether caching is enabled for this tool.
   * @param ttlSeconds The TTL in seconds for cache entries.
   */
  public CachingToolCallback(
      ToolCallback delegate, ToolCacheStore cacheService, boolean cacheEnabled, long ttlSeconds) {
    this.delegate = delegate;
    this.cacheService = cacheService;
    this.cacheEnabled = cacheEnabled;
    this.ttlSeconds = ttlSeconds;
  }

  /**
   * Retrieves the tool definition from the delegate.
   *
   * @return The tool definition containing its name, description, and schemas.
   */
  @Override
  public ToolDefinition getToolDefinition() {
    return delegate.getToolDefinition();
  }

  /**
   * Gets the identifier name of the delegate tool.
   *
   * @return The tool name.
   */
  private String getName() {
    return getToolDefinition().name();
  }

  /**
   * Executes the tool with the provided input parameters. Intercepts the request to check if a
   * cached response exists prior to delegating.
   *
   * @param input The JSON string representation of the tool input parameters.
   * @return The result of the tool execution as a String.
   */
  @Override
  public String call(String input) {
    if (!cacheEnabled) {
      return delegate.call(input);
    }

    log.debug("Intercepting call to tool '{}' with input: {}", getName(), input);
    String cached = cacheService.get(getName(), input);
    if (cached != null) {
      log.info("Cache hit for tool '{}'", getName());
      return cached;
    }

    log.debug("Cache miss for tool '{}', executing underlying logic...", getName());
    String result = delegate.call(input);

    log.info("Caching result for tool '{}' with TTL {}s", getName(), ttlSeconds);
    cacheService.put(getName(), input, result, ttlSeconds);

    return result;
  }
}
