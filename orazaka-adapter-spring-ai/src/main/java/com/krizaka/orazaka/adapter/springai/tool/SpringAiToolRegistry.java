package com.krizaka.orazaka.adapter.springai.tool;

import com.krizaka.orazaka.core.domain.model.chat.EngineTool;
import com.krizaka.orazaka.core.domain.ports.outbound.PlatformToolConfigProvider;
import com.krizaka.orazaka.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.orazaka.core.domain.ports.outbound.ToolResultCache;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

/**
 * Spring AI implementation of the {@link ToolRegistry} port: local Java functions registered as
 * {@link ToolCallback}s, wrapped by a {@link CachingToolCallback} when the platform's tool
 * configuration asks for caching. The tools themselves are registered by whoever owns them (the
 * {@code orazaka-tools} catalogue); this class knows nothing of them.
 */
public class SpringAiToolRegistry implements ToolRegistry {

  private static final long DEFAULT_CACHE_TTL_SECONDS = 3600L;

  private final PlatformToolConfigProvider platformToolConfigProvider;
  private final ToolResultCache cache;
  private final List<ToolCallback> registeredTools = new CopyOnWriteArrayList<>();

  /**
   * Creates the registry.
   *
   * @param platformToolConfigProvider Dynamic tool configuration provider (nullable).
   * @param cache Where cached tool results are kept.
   */
  public SpringAiToolRegistry(
      PlatformToolConfigProvider platformToolConfigProvider, ToolResultCache cache) {
    this.platformToolConfigProvider = platformToolConfigProvider;
    this.cache = cache;
  }

  /**
   * Registers a new Java function callback tool in the local registry.
   *
   * @param <I> The input argument type for the tool.
   * @param <O> The output result type from the tool.
   * @param name The name identifier of the tool.
   * @param description A descriptive string explaining what the tool does.
   * @param inputType The Class type representing the input.
   * @param function The functional logic representing the tool execution.
   */
  @Override
  public <I, O> void registerTool(
      String name, String description, Class<I> inputType, Function<I, O> function) {
    this.registeredTools.add(
        FunctionToolCallback.builder(name, function)
            .description(description)
            .inputType(inputType)
            .build());
  }

  /**
   * Retrieves all registered tool callbacks, wrapping any that have active caching properties
   * configured.
   *
   * @return An immutable List of engine tools, each keeping the {@link ToolCallback} it stands for.
   */
  @Override
  public List<EngineTool> getRegisteredTools() {
    return registeredTools.stream()
        .map(
            tool -> {
              String name = tool.getToolDefinition().name();
              if (platformToolConfigProvider == null) {
                return tool;
              }
              var configOpt = platformToolConfigProvider.getToolConfig(name);
              if (configOpt.isPresent()) {
                var config = configOpt.get();
                if (Boolean.TRUE.equals(config.cacheEnabled())) {
                  long ttl =
                      config.cacheTtlSeconds() != null
                          ? config.cacheTtlSeconds()
                          : DEFAULT_CACHE_TTL_SECONDS;
                  return new CachingToolCallback(tool, cache, true, ttl);
                }
              }
              return tool;
            })
        .map(SpringAiToolRegistry::toEngine)
        .toList();
  }

  private static EngineTool toEngine(ToolCallback tool) {
    var definition = tool.getToolDefinition();
    return new EngineTool(
        definition.name(), definition.description(), definition.inputSchema(), tool::call, tool);
  }
}
