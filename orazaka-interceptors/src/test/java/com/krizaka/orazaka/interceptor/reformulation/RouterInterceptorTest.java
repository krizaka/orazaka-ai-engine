package com.krizaka.orazaka.interceptor.reformulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.krizaka.orazaka.core.application.pipeline.PipelineOptionsRegistry;
import com.krizaka.orazaka.core.domain.model.PromptContext;
import com.krizaka.orazaka.core.infrastructure.config.CoreProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
class RouterInterceptorTest {

  @Mock private CoreProperties properties;

  @Mock private PipelineOptionsRegistry optionsRegistry;

  private Resource routerSystemResource;

  @BeforeEach
  void setUp() {
    routerSystemResource = new ByteArrayResource("system prompt".getBytes());
  }

  @Test
  void isAiDependent_returnsTrue() {
    var interceptor =
        new RouterInterceptor(Map.of(), properties, optionsRegistry, routerSystemResource);

    assertTrue(interceptor.isAiDependent());
  }

  @Test
  void intercept_whenDisabled_returnsContextUnchanged() {
    when(properties.orchestration()).thenReturn(null);

    var interceptor =
        new RouterInterceptor(Map.of(), properties, optionsRegistry, routerSystemResource);

    var context = new PromptContext("test query", Map.of());
    PromptContext result = interceptor.intercept(context);

    assertSame(context, result);
  }

  @Test
  void intercept_withNoRegisteredModels_returnsContextUnchanged() {
    var routerConfig = mock(CoreProperties.InterceptorConfig.class);
    when(routerConfig.provider()).thenReturn("nonexistent");

    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.router()).thenReturn(routerConfig);
    when(properties.orchestration()).thenReturn(orchestration);

    var interceptor =
        new RouterInterceptor(Map.of(), properties, optionsRegistry, routerSystemResource);

    var context = new PromptContext("test query", Map.of());
    PromptContext result = interceptor.intercept(context);

    assertSame(context, result);
  }

  @Test
  void constructor_nullChatModels_usesEmptyMap() {
    var interceptor =
        new RouterInterceptor(null, properties, optionsRegistry, routerSystemResource);
    assertNotNull(interceptor);
  }

  @Test
  void constructor_nullProperties_throwsNpe() {
    Map<String, org.springframework.ai.chat.model.ChatModel> models = Map.of();
    assertThrows(
        NullPointerException.class,
        () -> new RouterInterceptor(models, null, optionsRegistry, routerSystemResource));
  }

  @Test
  void intercept_whenEnabledAndRoutesSuccessfully_returnsContextWithRoutedProvider() {
    var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
    var chatResponse = mock(org.springframework.ai.chat.model.ChatResponse.class);
    var generation = mock(org.springframework.ai.chat.model.Generation.class);
    var assistantMessage = mock(org.springframework.ai.chat.messages.AssistantMessage.class);

    when(assistantMessage.getText()).thenReturn("ollama");
    when(generation.getOutput()).thenReturn(assistantMessage);
    when(chatResponse.getResult()).thenReturn(generation);
    when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(chatResponse);

    var routerConfig = mock(CoreProperties.InterceptorConfig.class);
    when(routerConfig.provider()).thenReturn("ollama");
    when(routerConfig.model()).thenReturn("llama3");

    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.router()).thenReturn(routerConfig);
    when(properties.orchestration()).thenReturn(orchestration);

    var options = mock(org.springframework.ai.chat.prompt.ChatOptions.class);
    when(optionsRegistry.build("ollama", "llama3", 0.0)).thenReturn(options);

    var interceptor =
        new RouterInterceptor(
            Map.of("ollama", chatModel), properties, optionsRegistry, routerSystemResource);

    var context = new PromptContext("test query", Map.of()).withRefinedPrompt("refined query");
    PromptContext result = interceptor.intercept(context);

    assertEquals("ollama", result.routedProvider());
  }

  @Test
  void intercept_onATurnCarryingMedia_leavesTheProviderToTheCaller() {
    // ADR-063: the router reads text and cannot tell which provider can see an image, and a routed
    // provider outranks the one the caller chose — a vision job naming its vision model would be
    // sent wherever the text reads best.
    var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.router()).thenReturn(mock(CoreProperties.InterceptorConfig.class));
    when(properties.orchestration()).thenReturn(orchestration);
    var interceptor =
        new RouterInterceptor(
            Map.of("ollama", chatModel, "openai", chatModel),
            properties,
            optionsRegistry,
            routerSystemResource);

    var context =
        new PromptContext(
            "Analyze this image", Map.of(), Map.of(), "Analyze this image", null, null, 1);
    PromptContext result = interceptor.intercept(context);

    assertSame(context, result);
    assertNull(result.routedProvider());
    verifyNoInteractions(chatModel);
  }

  @Test
  void intercept_withPartialMatchProvider_resolvesAndRoutesCorrectly() {
    var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
    var chatResponse = mock(org.springframework.ai.chat.model.ChatResponse.class);
    var generation = mock(org.springframework.ai.chat.model.Generation.class);
    var assistantMessage = mock(org.springframework.ai.chat.messages.AssistantMessage.class);

    when(assistantMessage.getText()).thenReturn("openai");
    when(generation.getOutput()).thenReturn(assistantMessage);
    when(chatResponse.getResult()).thenReturn(generation);
    when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(chatResponse);

    var routerConfig =
        mock(
            CoreProperties.InterceptorConfig
                .class); // Resolution logic will match "open" to "openai"
    when(routerConfig.provider()).thenReturn("open");
    when(routerConfig.model()).thenReturn("gpt-4");

    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.router()).thenReturn(routerConfig);
    when(properties.orchestration()).thenReturn(orchestration);

    var options = mock(org.springframework.ai.chat.prompt.ChatOptions.class);
    when(optionsRegistry.build("open", "gpt-4", 0.0)).thenReturn(options);

    var interceptor =
        new RouterInterceptor(
            Map.of("openai", chatModel), properties, optionsRegistry, routerSystemResource);

    var context = new PromptContext("test query", Map.of()).withRefinedPrompt("refined query");
    PromptContext result = interceptor.intercept(context);

    assertEquals("openai", result.routedProvider());
  }

  @Test
  void intercept_whenChatModelThrowsException_returnsUnmodifiedContext() {
    var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
    when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenThrow(new RuntimeException("LLM failure"));

    var routerConfig = mock(CoreProperties.InterceptorConfig.class);
    when(routerConfig.provider()).thenReturn("ollama");
    when(routerConfig.model()).thenReturn("llama3");

    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.router()).thenReturn(routerConfig);
    when(properties.orchestration()).thenReturn(orchestration);

    var options = mock(org.springframework.ai.chat.prompt.ChatOptions.class);
    when(optionsRegistry.build("ollama", "llama3", 0.0)).thenReturn(options);

    var interceptor =
        new RouterInterceptor(
            Map.of("ollama", chatModel), properties, optionsRegistry, routerSystemResource);

    var context = new PromptContext("test query", Map.of()).withRefinedPrompt("refined query");
    PromptContext result = interceptor.intercept(context);

    assertSame(context, result);
  }
}
