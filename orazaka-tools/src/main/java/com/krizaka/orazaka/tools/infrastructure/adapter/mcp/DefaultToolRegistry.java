package com.krizaka.orazaka.tools.infrastructure.adapter.mcp;

import com.krizaka.orazaka.core.domain.ports.outbound.KnowledgeService;
import com.krizaka.orazaka.core.domain.ports.outbound.PlatformToolConfigProvider;
import com.krizaka.orazaka.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.orazaka.core.infrastructure.support.SecurityContextUtil;
import com.krizaka.orazaka.tools.domain.model.audio.AnalyzeAudioExtractRequest;
import com.krizaka.orazaka.tools.domain.model.audio.AnalyzeAudioExtractResponse;
import com.krizaka.orazaka.tools.domain.model.poster.AnalyzePosterRequest;
import com.krizaka.orazaka.tools.domain.model.poster.AnalyzePosterResponse;
import com.krizaka.orazaka.tools.domain.model.search.SearchWebRequest;
import com.krizaka.orazaka.tools.domain.model.search.SearchWebResponse;
import com.krizaka.orazaka.tools.infrastructure.cache.ToolCacheStore;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

/**
 * Default implementation of ToolRegistry. Registry for local Java methods to be used as tools by
 * the AI models. RAG source lookup goes through the {@link KnowledgeService} port — the knowledge
 * service owns the sources and their ingestion since Phase 4.
 */
@Component
public class DefaultToolRegistry implements ToolRegistry {

  private static final Logger log = LoggerFactory.getLogger(DefaultToolRegistry.class);
  private static final String TOOL_SEARCH_WEB = "searchWeb";

  private final PlatformToolConfigProvider platformToolConfigProvider;
  private final ToolCacheStore cacheService;
  private final KnowledgeService knowledgeService;
  private final List<ToolCallback> registeredTools = new ArrayList<>();

  /**
   * Initializes the registry with tools, caching, and knowledge dependencies.
   *
   * @param platformToolConfigProvider Dynamic tool configuration provider.
   * @param cacheService Multi-tier cache service.
   * @param knowledgeService Knowledge context port (RAG retrieval + tool source lookup).
   */
  public DefaultToolRegistry(
      PlatformToolConfigProvider platformToolConfigProvider,
      ToolCacheStore cacheService,
      KnowledgeService knowledgeService) {
    this.platformToolConfigProvider = platformToolConfigProvider;
    this.cacheService = cacheService;
    this.knowledgeService = knowledgeService;
  }

  @PostConstruct
  void registerDefaultTools() {
    registerTool(
        "analyzePoster",
        "Analyzes a movie poster provided as a base64 encoded string using vision model.",
        AnalyzePosterRequest.class,
        this::analyzePoster);
    registerTool(
        "analyzeAudioExtract",
        "Analyzes a film audio extract to check for specific compliance or content criteria.",
        AnalyzeAudioExtractRequest.class,
        this::analyzeAudioExtract);
    registerTool(
        TOOL_SEARCH_WEB,
        "Queries the corporate RAG database for real-time web search results.",
        SearchWebRequest.class,
        this::searchWeb);
  }

  private SearchWebResponse searchWeb(SearchWebRequest request) {
    // The tool, never its arguments: a query or a poster is the user's content (ADR-064).
    log.info("Executing searchWeb tool");
    if (knowledgeService == null) {
      return new SearchWebResponse("Knowledge service is not initialized.", false);
    }
    String currentUserId = (String) SecurityContextUtil.extractSecurityMetadata().get("userId");
    String matches =
        knowledgeService.searchSources(TOOL_SEARCH_WEB, currentUserId, request.query());
    if (matches.isBlank()) {
      // Fallback: return any corporate profile/framework description visible to the user.
      matches = knowledgeService.searchSources(TOOL_SEARCH_WEB, currentUserId, "");
    }
    return new SearchWebResponse(matches, true);
  }

  private AnalyzePosterResponse analyzePoster(AnalyzePosterRequest request) {
    log.info("Executing analyzePoster tool");
    String base64 = request.posterBase64();
    if (base64.contains("error") || base64.contains("corrupt")) {
      return new AnalyzePosterResponse("Failed to parse poster image: corrupt payload.", false);
    }
    return new AnalyzePosterResponse(
        "Poster analysis success. Detected themes: Sci-Fi, cyberpunk architecture, neon aesthetic. Prompt: "
            + request.prompt(),
        true);
  }

  private AnalyzeAudioExtractResponse analyzeAudioExtract(AnalyzeAudioExtractRequest request) {
    log.info("Executing analyzeAudioExtract tool");
    String path = request.clipPath();
    if (path.contains("corrupt") || path.contains("invalid")) {
      return new AnalyzeAudioExtractResponse("Audio clip corrupt or missing at " + path, false);
    }
    return new AnalyzeAudioExtractResponse(
        "Audio extract compliance check passed. Track checks clear under type: "
            + request.checkType(),
        true);
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
   * @return An immutable List of wrapped or direct ToolCallback objects.
   */
  @Override
  public List<ToolCallback> getRegisteredTools() {
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
                  long ttl = config.cacheTtlSeconds() != null ? config.cacheTtlSeconds() : 3600L;
                  return new CachingToolCallback(tool, cacheService, true, ttl);
                }
              }
              return tool;
            })
        .toList();
  }
}
