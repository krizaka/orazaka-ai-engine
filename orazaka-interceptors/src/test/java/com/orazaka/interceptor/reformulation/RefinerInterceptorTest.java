package com.orazaka.interceptor.reformulation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.orazaka.core.application.pipeline.PipelineOptionsRegistry;
import com.orazaka.core.domain.model.PromptContext;
import com.orazaka.core.infrastructure.config.CoreProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
class RefinerInterceptorTest {

  @Mock private CoreProperties properties;

  @Mock private PipelineOptionsRegistry optionsRegistry;

  private Resource systemResource;
  private Resource contextResource;

  @BeforeEach
  void setUp() {
    systemResource = new ByteArrayResource("system prompt".getBytes());
    contextResource =
        new ByteArrayResource(
            "User: {userMetadata}\nSystem: {systemMetadata}\nQuery: {rawQuery}".getBytes());
  }

  @Test
  void isAiDependent_returnsTrue() {
    var interceptor =
        new RefinerInterceptor(
            Map.of(), properties, optionsRegistry, systemResource, contextResource);

    assertTrue(interceptor.isAiDependent());
  }

  @Test
  void intercept_whenDisabled_returnsContextUnchanged() {
    when(properties.orchestration()).thenReturn(null);

    var interceptor =
        new RefinerInterceptor(
            Map.of(), properties, optionsRegistry, systemResource, contextResource);

    var context = new PromptContext("test query", Map.of());
    PromptContext result = interceptor.intercept(context);

    assertSame(context, result);
  }

  @Test
  void intercept_withNoRegisteredModels_returnsContextUnchanged() {
    var refinerConfig = mock(CoreProperties.InterceptorConfig.class);
    when(refinerConfig.provider()).thenReturn("nonexistent");

    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.refiner()).thenReturn(refinerConfig);
    when(properties.orchestration()).thenReturn(orchestration);

    var interceptor =
        new RefinerInterceptor(
            Map.of(), properties, optionsRegistry, systemResource, contextResource);

    var context = new PromptContext("test query", Map.of());
    PromptContext result = interceptor.intercept(context);

    assertSame(context, result);
  }

  @Test
  void constructor_nullChatModels_usesEmptyMap() {
    var interceptor =
        new RefinerInterceptor(null, properties, optionsRegistry, systemResource, contextResource);

    assertNotNull(interceptor);
  }

  @Test
  void constructor_nullProperties_throwsNpe() {
    Map<String, org.springframework.ai.chat.model.ChatModel> models = Map.of();
    assertThrows(
        NullPointerException.class,
        () ->
            new RefinerInterceptor(models, null, optionsRegistry, systemResource, contextResource));
  }

  @Test
  void constructor_nullOptionsRegistry_throwsNpe() {
    Map<String, org.springframework.ai.chat.model.ChatModel> models = Map.of();
    assertThrows(
        NullPointerException.class,
        () -> new RefinerInterceptor(models, properties, null, systemResource, contextResource));
  }

  @Test
  void constructor_nullSystemResource_throwsNpe() {
    Map<String, org.springframework.ai.chat.model.ChatModel> models = Map.of();
    assertThrows(
        NullPointerException.class,
        () -> new RefinerInterceptor(models, properties, optionsRegistry, null, contextResource));
  }

  @Test
  void constructor_nullContextResource_throwsNpe() {
    Map<String, org.springframework.ai.chat.model.ChatModel> models = Map.of();
    assertThrows(
        NullPointerException.class,
        () -> new RefinerInterceptor(models, properties, optionsRegistry, systemResource, null));
  }

  @Test
  void intercept_whenEnabledAndRefinesSuccessfully_returnsContextWithRefinedPrompt() {
    var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
    var chatResponse = mock(org.springframework.ai.chat.model.ChatResponse.class);
    var generation = mock(org.springframework.ai.chat.model.Generation.class);
    var assistantMessage = mock(org.springframework.ai.chat.messages.AssistantMessage.class);

    when(assistantMessage.getText()).thenReturn("highly refined query");
    when(generation.getOutput()).thenReturn(assistantMessage);
    when(chatResponse.getResult()).thenReturn(generation);
    when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(chatResponse);

    var refinerConfig = mock(CoreProperties.InterceptorConfig.class);
    when(refinerConfig.provider()).thenReturn("ollama");
    when(refinerConfig.model()).thenReturn("llama3");
    when(refinerConfig.temperature()).thenReturn(0.7);

    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.refiner()).thenReturn(refinerConfig);
    when(properties.orchestration()).thenReturn(orchestration);

    var options = mock(org.springframework.ai.chat.prompt.ChatOptions.class);
    when(optionsRegistry.build("ollama", "llama3", 0.7)).thenReturn(options);

    var interceptor =
        new RefinerInterceptor(
            Map.of("ollama", chatModel),
            properties,
            optionsRegistry,
            systemResource,
            contextResource);

    var context = new PromptContext("test query", Map.of());
    PromptContext result = interceptor.intercept(context);

    assertEquals("highly refined query", result.refinedPrompt());
  }

  @Test
  void intercept_onATurnCarryingMedia_keepsTheUsersWords() {
    // ADR-063: the refinement envelope never carries the image. A rewrite of "t'en penses quoi ?"
    // made without the photograph is what the vision model would receive beside it.
    var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.refiner()).thenReturn(mock(CoreProperties.InterceptorConfig.class));
    when(properties.orchestration()).thenReturn(orchestration);
    var interceptor =
        new RefinerInterceptor(
            Map.of("ollama", chatModel),
            properties,
            optionsRegistry,
            systemResource,
            contextResource);

    var context =
        new PromptContext(
            "t'en penses quoi ?", Map.of(), Map.of(), "t'en penses quoi ?", null, null, 1);
    PromptContext result = interceptor.intercept(context);

    assertSame(context, result);
    assertEquals("t'en penses quoi ?", result.refinedPrompt());
    verifyNoInteractions(chatModel);
  }

  @Test
  void intercept_withPartialMatchProvider_resolvesAndRefinesCorrectly() {
    var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
    var chatResponse = mock(org.springframework.ai.chat.model.ChatResponse.class);
    var generation = mock(org.springframework.ai.chat.model.Generation.class);
    var assistantMessage = mock(org.springframework.ai.chat.messages.AssistantMessage.class);

    when(assistantMessage.getText()).thenReturn("highly refined query partial");
    when(generation.getOutput()).thenReturn(assistantMessage);
    when(chatResponse.getResult()).thenReturn(generation);
    when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(chatResponse);

    var refinerConfig = mock(CoreProperties.InterceptorConfig.class);
    when(refinerConfig.provider()).thenReturn("open"); // partial for openai
    when(refinerConfig.model()).thenReturn("gpt-4");
    when(refinerConfig.temperature()).thenReturn(0.5);

    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.refiner()).thenReturn(refinerConfig);
    when(properties.orchestration()).thenReturn(orchestration);

    var options = mock(org.springframework.ai.chat.prompt.ChatOptions.class);
    when(optionsRegistry.build("open", "gpt-4", 0.5)).thenReturn(options);

    var interceptor =
        new RefinerInterceptor(
            Map.of("openai", chatModel),
            properties,
            optionsRegistry,
            systemResource,
            contextResource);

    var context = new PromptContext("test query", Map.of());
    PromptContext result = interceptor.intercept(context);

    assertEquals("highly refined query partial", result.refinedPrompt());
  }

  @Test
  void intercept_whenChatModelThrowsException_returnsUnmodifiedContext() {
    var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
    when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenThrow(new RuntimeException("LLM failure"));

    var refinerConfig = mock(CoreProperties.InterceptorConfig.class);
    when(refinerConfig.provider()).thenReturn("ollama");
    when(refinerConfig.model()).thenReturn("llama3");
    when(refinerConfig.temperature()).thenReturn(0.7);

    var orchestration = mock(CoreProperties.OrchestrationConfig.class);
    when(orchestration.refiner()).thenReturn(refinerConfig);
    when(properties.orchestration()).thenReturn(orchestration);

    var options = mock(org.springframework.ai.chat.prompt.ChatOptions.class);
    when(optionsRegistry.build("ollama", "llama3", 0.7)).thenReturn(options);

    var interceptor =
        new RefinerInterceptor(
            Map.of("ollama", chatModel),
            properties,
            optionsRegistry,
            systemResource,
            contextResource);

    var context = new PromptContext("test query", Map.of());
    PromptContext result = interceptor.intercept(context);

    assertSame(context, result);
  }
}
