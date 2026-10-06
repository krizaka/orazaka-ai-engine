package com.orazaka.core.infrastructure.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.orazaka.core.application.engine.Engine;
import com.orazaka.core.domain.model.Context;
import com.orazaka.core.domain.model.chat.ChatRequest;
import com.orazaka.core.domain.model.chat.ChatResponse;
import com.orazaka.core.domain.model.chat.InternalChatRequest;
import com.orazaka.core.domain.model.chat.InternalChatResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class ChatGeneratorClientImplTest {

  private Engine engine;
  private ChatGeneratorClientImpl client;

  @BeforeEach
  void setUp() {
    engine = mock(Engine.class);
    client = new ChatGeneratorClientImpl(engine);
  }

  @Test
  void constructorRejectsNullEngine() {
    assertThatThrownBy(() -> new ChatGeneratorClientImpl(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void generateChat_delegatesToEngineAndMapsResponse() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Hello");
    var request = new ChatRequest("Hello", List.of(msg), Map.of(), ctx);

    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("Hi there", "conv-1", Map.of()));

    ChatResponse response = client.generateChat(request);

    assertThat(response.content()).isEqualTo("Hi there");
    assertThat(response.conversationId()).isEqualTo("conv-1");
    verify(engine).chat(any(InternalChatRequest.class));
  }

  @Test
  void generateChat_withOllamaSettings() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Test");
    var settings =
        Map.<String, Object>of("provider", "ollama", "model", "llama3", "temperature", 0.7);
    var request = new ChatRequest("Test", List.of(msg), settings, ctx);

    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("OK", "conv-2", Map.of()));

    ChatResponse response = client.generateChat(request);
    assertThat(response.content()).isEqualTo("OK");
  }

  @Test
  void generateChat_withOpenAiSettings() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Test");
    var settings =
        Map.<String, Object>of("provider", "openai", "model", "gpt-4", "temperature", 0.5);
    var request = new ChatRequest("Test", List.of(msg), settings, ctx);

    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("Done", "conv-3", Map.of()));

    ChatResponse response = client.generateChat(request);
    assertThat(response.content()).isEqualTo("Done");
  }

  @Test
  void generateChat_withNullSettings() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Hi");
    var request = new ChatRequest("Hi", List.of(msg), null, ctx);

    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("Response", null, Map.of()));

    ChatResponse response = client.generateChat(request);
    assertThat(response.content()).isEqualTo("Response");
  }

  @Test
  void generateChat_withEmptySettings() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Hi");
    var request = new ChatRequest("Hi", List.of(msg), Map.of(), ctx);

    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("R", null, Map.of()));

    ChatResponse response = client.generateChat(request);
    assertThat(response.content()).isEqualTo("R");
  }

  @Test
  void generateChat_withUnknownProvider() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Hi");
    var settings = Map.<String, Object>of("provider", "anthropic", "model", "claude-3");
    var request = new ChatRequest("Hi", List.of(msg), settings, ctx);

    when(engine.chat(any(InternalChatRequest.class)))
        .thenReturn(new InternalChatResponse("R", null, Map.of()));

    ChatResponse response = client.generateChat(request);
    assertThat(response).isNotNull();
  }

  @Test
  void streamChat_delegatesToEngineStream() {
    var ctx = Context.anonymous();
    var msg = new ChatRequest.ChatMessage("user", "Stream me");
    var request = new ChatRequest("Stream me", List.of(msg), Map.of(), ctx);

    when(engine.stream(any(InternalChatRequest.class)))
        .thenReturn(
            Flux.just(
                new InternalChatResponse("chunk1", "conv-s", Map.of()),
                new InternalChatResponse("chunk2", "conv-s", Map.of())));

    List<ChatResponse> responses = client.streamChat(request).collectList().block();

    assertThat(responses).hasSize(2);
    assertThat(responses.get(0).content()).isEqualTo("chunk1");
    assertThat(responses.get(1).content()).isEqualTo("chunk2");
  }
}
