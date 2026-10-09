package com.krizaka.orazaka.tools.infrastructure.adapter.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.orazaka.core.domain.ports.outbound.KnowledgeService;
import com.krizaka.orazaka.core.domain.ports.outbound.PlatformToolConfigProvider;
import com.krizaka.orazaka.core.infrastructure.support.SecurityContextUtil;
import com.krizaka.orazaka.tools.infrastructure.cache.ToolCacheStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;

@ExtendWith(MockitoExtension.class)
class DefaultToolRegistryTest {

  @Mock private PlatformToolConfigProvider platformToolConfigProvider;

  @Mock private ToolCacheStore cacheService;

  @Mock private KnowledgeService knowledgeService;

  private DefaultToolRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new DefaultToolRegistry(platformToolConfigProvider, cacheService, knowledgeService);
    registry.registerDefaultTools();
  }

  @Test
  void shouldRegisterTool() {
    String name = "testTool";
    String description = "A test tool";
    Function<String, String> function = input -> "Hello " + input;

    registry.registerTool(name, description, String.class, function);

    List<ToolCallback> tools = registry.getRegisteredTools();
    assertThat(tools).hasSize(4);
    assertThat(tools.stream().map(t -> t.getToolDefinition().name()))
        .contains(name, "analyzePoster", "analyzeAudioExtract", "searchWeb");
  }

  @Test
  void shouldReturnImmutableList() {
    registry.registerTool("tool1", "desc1", String.class, (String s) -> s);
    List<ToolCallback> tools = registry.getRegisteredTools();
    assertThrows(UnsupportedOperationException.class, () -> tools.add(null));
  }

  @ParameterizedTest
  @CsvSource({
    "analyzePoster, '{\"posterBase64\":\"valid-base64\",\"prompt\":\"cat\"}', 'Poster analysis success'",
    "analyzePoster, '{\"posterBase64\":\"corrupt-data\",\"prompt\":\"cat\"}', 'Failed to parse poster image'",
    "analyzeAudioExtract, '{\"clipPath\":\"valid-path.mp3\",\"checkType\":\"loudness\"}', 'Audio extract compliance check passed'",
    "analyzeAudioExtract, '{\"clipPath\":\"corrupt-path.mp3\",\"checkType\":\"loudness\"}', 'Audio clip corrupt or missing'"
  })
  void testToolCallbacks(String toolName, String inputJson, String expectedSubstring) {
    ToolCallback tool =
        registry.getRegisteredTools().stream()
            .filter(t -> toolName.equals(t.getToolDefinition().name()))
            .findFirst()
            .orElseThrow();

    String responseJson = tool.call(inputJson);
    assertNotNull(responseJson);
    assertTrue(responseJson.contains(expectedSubstring));
  }

  @Test
  void searchWeb_knowledgeServiceNull_returnsErrorResponse() {
    DefaultToolRegistry registryNoKnowledge = new DefaultToolRegistry(null, null, null);
    registryNoKnowledge.registerDefaultTools();
    ToolCallback tool =
        registryNoKnowledge.getRegisteredTools().stream()
            .filter(t -> "searchWeb".equals(t.getToolDefinition().name()))
            .findFirst()
            .orElseThrow();

    String response = tool.call("{\"query\":\"test\"}");
    assertTrue(response.contains("Knowledge service is not initialized"));
  }

  @Test
  void searchWeb_withMatches_returnsJoinedContent() {
    ToolCallback tool =
        registry.getRegisteredTools().stream()
            .filter(t -> "searchWeb".equals(t.getToolDefinition().name()))
            .findFirst()
            .orElseThrow();

    when(knowledgeService.searchSources("searchWeb", "user-123", "Corporate"))
        .thenReturn("Corporate description profile content.");

    // Stub SecurityContextUtil using MockedStatic to return userId: user-123
    try (MockedStatic<SecurityContextUtil> mockSecurity = mockStatic(SecurityContextUtil.class)) {
      mockSecurity
          .when(SecurityContextUtil::extractSecurityMetadata)
          .thenReturn(Map.of("userId", "user-123"));

      String response = tool.call("{\"query\":\"Corporate\"}");
      assertTrue(response.contains("Corporate description profile content"));
    }
  }

  @Test
  void searchWeb_noMatch_fallsBackToAllVisibleSources() {
    ToolCallback tool =
        registry.getRegisteredTools().stream()
            .filter(t -> "searchWeb".equals(t.getToolDefinition().name()))
            .findFirst()
            .orElseThrow();

    when(knowledgeService.searchSources("searchWeb", "user-123", "Corporate")).thenReturn("");
    when(knowledgeService.searchSources("searchWeb", "user-123", ""))
        .thenReturn("Corporate fallback profile.");

    try (MockedStatic<SecurityContextUtil> mockSecurity = mockStatic(SecurityContextUtil.class)) {
      mockSecurity
          .when(SecurityContextUtil::extractSecurityMetadata)
          .thenReturn(Map.of("userId", "user-123"));

      String response = tool.call("{\"query\":\"Corporate\"}");
      assertTrue(response.contains("Corporate fallback profile"));
    }
  }

  @Test
  void getRegisteredTools_cacheEnabledInConfig_returnsWrappedCallback() {
    PlatformToolConfigProvider.PlatformToolConfig config =
        new PlatformToolConfigProvider.PlatformToolConfig(
            1, "analyzePoster", true, 600, false, null, null);
    when(platformToolConfigProvider.getToolConfig("analyzePoster")).thenReturn(Optional.of(config));

    List<ToolCallback> tools = registry.getRegisteredTools();
    ToolCallback posterTool =
        tools.stream()
            .filter(t -> "analyzePoster".equals(t.getToolDefinition().name()))
            .findFirst()
            .orElseThrow();

    assertTrue(posterTool instanceof CachingToolCallback);
  }
}
