package com.orazaka.interceptor.tooling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.orazaka.core.domain.model.chat.InternalChatRequest;
import com.orazaka.core.domain.ports.outbound.ToolRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

class ToolInterceptorTest {

  @Test
  @DisplayName("preProcess returns original options when streaming is true")
  void preProcessStreaming() {
    ToolRegistry toolRegistry = mock(ToolRegistry.class);
    ToolInterceptor interceptor = new ToolInterceptor(toolRegistry);

    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.streaming()).thenReturn(true);
    ChatOptions options = mock(ChatOptions.class);

    ChatOptions result = interceptor.preProcess(request, "prompt", new ArrayList<>(), options);
    assertThat(result).isSameAs(options);
    verifyNoInteractions(toolRegistry);
  }

  @Test
  @DisplayName("preProcess returns original options when model name contains vision keywords")
  void preProcessVisionModel() {
    ToolRegistry toolRegistry = mock(ToolRegistry.class);
    ToolInterceptor interceptor = new ToolInterceptor(toolRegistry);

    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.streaming()).thenReturn(false);

    ChatOptions options = mock(ChatOptions.class);
    when(options.getModel()).thenReturn("llama3.2-vision");

    ChatOptions result = interceptor.preProcess(request, "prompt", new ArrayList<>(), options);
    assertThat(result).isSameAs(options);
    verifyNoInteractions(toolRegistry);
  }

  @Test
  @DisplayName("preProcess returns original options when toolRegistry is null or empty")
  void preProcessRegistryNullOrEmpty() {
    ToolInterceptor interceptorNull = new ToolInterceptor(null);
    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.streaming()).thenReturn(false);
    ChatOptions options = mock(ChatOptions.class);

    ChatOptions resultNull =
        interceptorNull.preProcess(request, "prompt", new ArrayList<>(), options);
    assertThat(resultNull).isSameAs(options);

    ToolRegistry toolRegistry = mock(ToolRegistry.class);
    when(toolRegistry.getRegisteredTools()).thenReturn(Collections.emptyList());
    ToolInterceptor interceptorEmpty = new ToolInterceptor(toolRegistry);

    ChatOptions resultEmpty =
        interceptorEmpty.preProcess(request, "prompt", new ArrayList<>(), options);
    assertThat(resultEmpty).isSameAs(options);
  }

  @Test
  @DisplayName("preProcess returns original options when no tools are demanded by keywords")
  void preProcessNoDemandedTools() {
    ToolRegistry toolRegistry = mock(ToolRegistry.class);

    ToolCallback posterTool = mock(ToolCallback.class);
    ToolDefinition posterDef = mock(ToolDefinition.class);
    when(posterDef.name()).thenReturn("analyzePoster");
    when(posterTool.getToolDefinition()).thenReturn(posterDef);

    when(toolRegistry.getRegisteredTools()).thenReturn(List.of(posterTool));
    ToolInterceptor interceptor = new ToolInterceptor(toolRegistry);

    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.streaming()).thenReturn(false);
    when(request.prompt()).thenReturn("hello standard text");
    when(request.messages()).thenReturn(Collections.emptyList());

    ChatOptions options = mock(ChatOptions.class);
    ChatOptions result = interceptor.preProcess(request, "just hello", new ArrayList<>(), options);
    assertThat(result).isSameAs(options);
  }

  @Test
  @DisplayName("preProcess attaches specialized poster tool to Ollama options when demanded")
  void preProcessOllamaPosterDemanded() {
    ToolRegistry toolRegistry = mock(ToolRegistry.class);

    ToolCallback posterTool = mock(ToolCallback.class);
    ToolDefinition posterDef = mock(ToolDefinition.class);
    when(posterDef.name()).thenReturn("analyzePoster");
    when(posterTool.getToolDefinition()).thenReturn(posterDef);

    ToolCallback genericTool = mock(ToolCallback.class);
    ToolDefinition genericDef = mock(ToolDefinition.class);
    when(genericDef.name()).thenReturn("genericCalculator");
    when(genericTool.getToolDefinition()).thenReturn(genericDef);

    when(toolRegistry.getRegisteredTools()).thenReturn(List.of(posterTool, genericTool));
    ToolInterceptor interceptor = new ToolInterceptor(toolRegistry);

    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.streaming()).thenReturn(false);
    when(request.prompt()).thenReturn("show me the poster info");
    when(request.messages()).thenReturn(Collections.emptyList());

    OllamaChatOptions options =
        OllamaChatOptions.builder().model("llama3.2").temperature(0.7).numPredict(128).build();

    ChatOptions result = interceptor.preProcess(request, "", new ArrayList<>(), options);
    assertThat(result).isInstanceOf(OllamaChatOptions.class);
    OllamaChatOptions ollamaResult = (OllamaChatOptions) result;

    assertThat(ollamaResult.getModel()).isEqualTo("llama3.2");
    assertThat(ollamaResult.getTemperature()).isEqualTo(0.7);
    assertThat(ollamaResult.getNumPredict()).isEqualTo(128);
    assertThat(ollamaResult.getToolCallbacks()).containsExactlyInAnyOrder(posterTool, genericTool);
  }

  @Test
  @DisplayName(
      "preProcess attaches specialized audio tool to OpenAI options when demanded via user message list")
  void preProcessOpenAiAudioDemanded() {
    ToolRegistry toolRegistry = mock(ToolRegistry.class);

    ToolCallback audioTool = mock(ToolCallback.class);
    ToolDefinition audioDef = mock(ToolDefinition.class);
    when(audioDef.name()).thenReturn("analyzeAudioExtract");
    when(audioTool.getToolDefinition()).thenReturn(audioDef);

    when(toolRegistry.getRegisteredTools()).thenReturn(List.of(audioTool));
    ToolInterceptor interceptor = new ToolInterceptor(toolRegistry);

    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.streaming()).thenReturn(false);
    when(request.prompt()).thenReturn("standard prompt");
    List<InternalChatRequest.ChatMessage> userMsgs =
        List.of(new InternalChatRequest.ChatMessage("user", "can you extract the audio?"));
    when(request.messages()).thenReturn(userMsgs);

    OpenAiChatOptions options =
        OpenAiChatOptions.builder().model("gpt-4o").temperature(0.5).maxTokens(100).build();

    ChatOptions result = interceptor.preProcess(request, null, new ArrayList<>(), options);
    assertThat(result).isInstanceOf(OpenAiChatOptions.class);
    OpenAiChatOptions openAiResult = (OpenAiChatOptions) result;

    assertThat(openAiResult.getModel()).isEqualTo("gpt-4o");
    assertThat(openAiResult.getTemperature()).isEqualTo(0.5);
    assertThat(openAiResult.getMaxTokens()).isEqualTo(100);
    assertThat(openAiResult.getToolCallbacks()).containsExactly(audioTool);
  }

  @Test
  @DisplayName("preProcess returns original options if custom options class is used")
  void preProcessCustomOptions() {
    ToolRegistry toolRegistry = mock(ToolRegistry.class);

    ToolCallback genericTool = mock(ToolCallback.class);
    ToolDefinition genericDef = mock(ToolDefinition.class);
    when(genericDef.name()).thenReturn("genericTool");
    when(genericTool.getToolDefinition()).thenReturn(genericDef);

    when(toolRegistry.getRegisteredTools()).thenReturn(List.of(genericTool));
    ToolInterceptor interceptor = new ToolInterceptor(toolRegistry);

    InternalChatRequest request = mock(InternalChatRequest.class);
    when(request.streaming()).thenReturn(false);
    when(request.prompt()).thenReturn("text");
    when(request.messages()).thenReturn(Collections.emptyList());

    ChatOptions options = mock(ChatOptions.class);
    ChatOptions result = interceptor.preProcess(request, "", new ArrayList<>(), options);
    assertThat(result).isSameAs(options);
  }
}
