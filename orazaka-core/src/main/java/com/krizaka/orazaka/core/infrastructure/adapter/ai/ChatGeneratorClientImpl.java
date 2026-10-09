package com.krizaka.orazaka.core.infrastructure.adapter.ai;

import com.krizaka.orazaka.core.application.engine.Engine;
import com.krizaka.orazaka.core.domain.model.chat.ChatRequest;
import com.krizaka.orazaka.core.domain.model.chat.ChatResponse;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatResponse;
import com.krizaka.orazaka.core.domain.ports.outbound.ChatGeneratorClient;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Outbound adapter implementing {@link ChatGeneratorClient} by delegating inference execution to
 * the core Engine.
 */
@Component
class ChatGeneratorClientImpl implements ChatGeneratorClient {

  private final Engine engine;

  public ChatGeneratorClientImpl(Engine engine) {
    this.engine = Objects.requireNonNull(engine, "Engine must not be null");
  }

  @Override
  public ChatResponse generateChat(ChatRequest request) {
    InternalChatRequest internalReq = mapChatRequest(request, false);
    InternalChatResponse internalResp = engine.chat(internalReq);
    return mapChatResponse(internalResp);
  }

  @Override
  public Flux<ChatResponse> streamChat(ChatRequest request) {
    InternalChatRequest internalReq = mapChatRequest(request, true);
    return engine.stream(internalReq).map(this::mapChatResponse);
  }

  private static InternalChatRequest.ChatMessage mapChatMessage(ChatRequest.ChatMessage msg) {
    return new InternalChatRequest.ChatMessage(msg.role(), msg.content());
  }

  private static InternalChatRequest mapChatRequest(ChatRequest req, boolean streaming) {
    List<InternalChatRequest.ChatMessage> mappedMessages =
        req.messages().stream().map(ChatGeneratorClientImpl::mapChatMessage).toList();

    ChatOptions options = buildChatOptions(req.settings());
    return new InternalChatRequest(req.prompt(), mappedMessages, options, req.context(), streaming);
  }

  private static ChatOptions buildChatOptions(Map<String, Object> settings) {
    if (settings == null || settings.isEmpty()) {
      return null;
    }
    String provider = (String) settings.get("provider");
    if (provider == null) {
      return null;
    }
    String model = (String) settings.get("model");
    Double temperature =
        settings.get("temperature") instanceof Number num ? num.doubleValue() : null;

    if ("ollama".equalsIgnoreCase(provider)) {
      return buildOllamaOptions(model, temperature);
    }
    if ("openai".equalsIgnoreCase(provider)) {
      return buildOpenAiOptions(model, temperature);
    }
    return null;
  }

  private static ChatOptions buildOllamaOptions(String model, Double temperature) {
    var builder = OllamaChatOptions.builder();
    if (model != null) builder.model(model);
    if (temperature != null) builder.temperature(temperature);
    return builder.build();
  }

  private static ChatOptions buildOpenAiOptions(String model, Double temperature) {
    var builder = OpenAiChatOptions.builder();
    if (model != null) builder.model(model);
    if (temperature != null) builder.temperature(temperature);
    return builder.build();
  }

  private ChatResponse mapChatResponse(InternalChatResponse resp) {
    // tokenUsage crosses the port. It was dropped here, which left every AiClient caller with no
    // measurement and made an asynchronous chat step unbillable (ADR-041).
    return new ChatResponse(
        resp.content(), resp.conversationId(), resp.tokenUsage(), resp.metadata());
  }
}
