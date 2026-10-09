package com.krizaka.orazaka.core.application.pipeline;

import static com.krizaka.orazaka.test.TestConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.orazaka.core.domain.event.ChatCompletedEvent;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class EngineStreamBridgeTest {

  @Mock private ChatModel chatModel;
  @Mock private EnginePipelineBridge pipelineBridge;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private DynamicPipelineExecutor orchestrationPipeline;
  @Mock private PromptContextInterceptor interceptor;

  @Mock
  private com.krizaka.orazaka.core.domain.ports.outbound.UserCredentialsProvider
      credentialsProvider;

  @Mock private com.krizaka.orazaka.core.application.routing.DynamicChatModelFactory modelFactory;

  private EngineStreamBridge streamBridge;

  @BeforeEach
  void setUp() {
    streamBridge =
        new EngineStreamBridge(chatModel, pipelineBridge, credentialsProvider, modelFactory);
  }

  @Test
  void shouldStreamChatResponsesSuccessfully() {
    // Arrange
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    var prompt = new Prompt(PROMPT_HELLO, OllamaChatOptions.builder().build());
    var context = new EnginePipelineContext(PROVIDER_OLLAMA, CONV_1, PROMPT_HELLO, prompt);

    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor)))
        .thenReturn(context);

    var mockOut1 = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOut1.getText()).thenReturn("chunk 1");
    var gen1 = new Generation(mockOut1, ChatGenerationMetadata.NULL);
    var response1 = new ChatResponse(List.of(gen1));

    var mockOut2 = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOut2.getText()).thenReturn("chunk 2");
    var gen2 = new Generation(mockOut2, ChatGenerationMetadata.NULL);
    var response2 = new ChatResponse(List.of(gen2));

    when(chatModel.stream(prompt)).thenReturn(Flux.just(response1, response2));

    // Act
    Flux<InternalChatResponse> resultFlux =
        streamBridge.createStream(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor), eventPublisher);

    List<InternalChatResponse> results = resultFlux.collectList().block();

    // Assert
    assertThat(results).isNotNull().hasSize(2);
    assertThat(results.get(0).content()).isEqualTo("chunk 1");
    assertThat(results.get(0).conversationId()).isEqualTo(CONV_1);
    assertThat(results.get(0).metadata()).containsEntry("provider", PROVIDER_OLLAMA);

    assertThat(results.get(1).content()).isEqualTo("chunk 2");
    assertThat(results.get(1).conversationId()).isEqualTo(CONV_1);
    assertThat(results.get(1).metadata()).containsEntry("provider", PROVIDER_OLLAMA);

    verify(eventPublisher, times(1)).publishEvent(any(ChatCompletedEvent.class));
    verify(interceptor, times(1)).postProcess(request, PROMPT_HELLO, "chunk 1chunk 2");
  }

  @Test
  void shouldFallbackToNonToolStreamOnSynchronousException() {
    // Arrange
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    var prompt = new Prompt(PROMPT_HELLO, OllamaChatOptions.builder().build());
    var context = new EnginePipelineContext(PROVIDER_OLLAMA, CONV_1, PROMPT_HELLO, prompt);

    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor)))
        .thenReturn(context);

    // Sync throw of IllegalStateException
    when(chatModel.stream(prompt)).thenThrow(new IllegalStateException("Tool activation failed"));

    var mockOutFallback = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOutFallback.getText()).thenReturn("fallback chunk");
    var gen = new Generation(mockOutFallback, ChatGenerationMetadata.NULL);
    var responseFallback = new ChatResponse(List.of(gen));

    // Fallback stream when input is not prompt
    when(chatModel.stream((Prompt) argThat(p -> p != prompt)))
        .thenReturn(Flux.just(responseFallback));

    // Act
    Flux<InternalChatResponse> resultFlux =
        streamBridge.createStream(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor), eventPublisher);

    List<InternalChatResponse> results = resultFlux.collectList().block();

    // Assert
    assertThat(results).isNotNull().hasSize(1);
    assertThat(results.get(0).content()).isEqualTo("fallback chunk");

    verify(eventPublisher, times(1)).publishEvent(any(ChatCompletedEvent.class));
  }

  @Test
  void shouldFallbackToNonToolStreamOnAsynchronousException() {
    // Arrange
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    var prompt = new Prompt(PROMPT_HELLO, OllamaChatOptions.builder().build());
    var context = new EnginePipelineContext(PROVIDER_OLLAMA, CONV_1, PROMPT_HELLO, prompt);

    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor)))
        .thenReturn(context);

    // Async emission of IllegalStateException
    when(chatModel.stream(prompt))
        .thenReturn(Flux.error(new IllegalStateException("Tool activation async failed")));

    var mockOutFallback = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOutFallback.getText()).thenReturn("fallback chunk async");
    var gen = new Generation(mockOutFallback, ChatGenerationMetadata.NULL);
    var responseFallback = new ChatResponse(List.of(gen));

    // Fallback stream when input is not prompt
    when(chatModel.stream((Prompt) argThat(p -> p != prompt)))
        .thenReturn(Flux.just(responseFallback));

    // Act
    Flux<InternalChatResponse> resultFlux =
        streamBridge.createStream(
            request, orchestrationPipeline, PROVIDER_OLLAMA, List.of(interceptor), eventPublisher);

    List<InternalChatResponse> results = resultFlux.collectList().block();

    // Assert
    assertThat(results).isNotNull().hasSize(1);
    assertThat(results.get(0).content()).isEqualTo("fallback chunk async");

    verify(eventPublisher, times(1)).publishEvent(any(ChatCompletedEvent.class));
  }

  @Test
  void shouldStreamWithCommercialModelWhenApiKeyIsFound() {
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    // Use OpenAiChatOptions to cover OpenAi path in option extraction
    var options = OpenAiChatOptions.builder().model("gpt-4o").build();
    var prompt = new Prompt(PROMPT_HELLO, options);
    var context = new EnginePipelineContext("openai", CONV_1, PROMPT_HELLO, prompt);

    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, "openai", List.of(interceptor)))
        .thenReturn(context);

    // Mock decrypted API key
    String userId = request.context().userId();
    when(credentialsProvider.getDecryptedApiKey(userId, "openai"))
        .thenReturn(Optional.of("sk-mock-key"));

    // Mock custom chat model from factory
    ChatModel mockOpenAiModel = mock(ChatModel.class);
    when(modelFactory.createChatModel("openai", "gpt-4o", "sk-mock-key"))
        .thenReturn(mockOpenAiModel);

    // Mock stream output
    var mockOut = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOut.getText()).thenReturn("openai chunk");
    var gen = new Generation(mockOut, ChatGenerationMetadata.NULL);
    var response = new ChatResponse(List.of(gen));
    when(mockOpenAiModel.stream(prompt)).thenReturn(Flux.just(response));

    // Act
    Flux<InternalChatResponse> resultFlux =
        streamBridge.createStream(
            request, orchestrationPipeline, "openai", List.of(interceptor), eventPublisher);

    List<InternalChatResponse> results = resultFlux.collectList().block();

    // Assert
    assertThat(results).isNotNull().hasSize(1);
    assertThat(results.get(0).content()).isEqualTo("openai chunk");
    assertThat(results.get(0).metadata()).containsEntry("provider", "openai");
  }

  @Test
  void shouldFallbackToLocalModelWhenCommercialApiKeyIsMissing() {
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    var prompt = new Prompt(PROMPT_HELLO, OllamaChatOptions.builder().build());
    var context = new EnginePipelineContext("openai", CONV_1, PROMPT_HELLO, prompt);

    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, "openai", List.of(interceptor)))
        .thenReturn(context);

    // Mock missing key
    String userId = request.context().userId();
    when(credentialsProvider.getDecryptedApiKey(userId, "openai")).thenReturn(Optional.empty());

    var mockOut = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOut.getText()).thenReturn("local fallback chunk");
    var gen = new Generation(mockOut, ChatGenerationMetadata.NULL);
    var response = new ChatResponse(List.of(gen));
    when(chatModel.stream(prompt)).thenReturn(Flux.just(response));

    // Act
    Flux<InternalChatResponse> resultFlux =
        streamBridge.createStream(
            request, orchestrationPipeline, "openai", List.of(interceptor), eventPublisher);

    List<InternalChatResponse> results = resultFlux.collectList().block();

    // Assert
    assertThat(results).isNotNull().hasSize(1);
    assertThat(results.get(0).content()).isEqualTo("local fallback chunk");
  }

  @Test
  void shouldExtractModelNamesForAnthropicAndGoogleOptions() {
    // 1. Anthropic Options
    var anthropicOptions = AnthropicChatOptions.builder().model("claude-3").build();
    var anthropicPrompt = new Prompt(PROMPT_HELLO, anthropicOptions);
    var anthropicContext =
        new EnginePipelineContext("anthropic", CONV_1, PROMPT_HELLO, anthropicPrompt);

    // 2. Google Options
    var googleOptions = GoogleGenAiChatOptions.builder().model("gemini-pro").build();
    var googlePrompt = new Prompt(PROMPT_HELLO, googleOptions);
    var googleContext = new EnginePipelineContext("google", CONV_1, PROMPT_HELLO, googlePrompt);

    // Let's verify we hit createChatModel with correct resolved model names
    var request = InternalChatRequest.simple(PROMPT_HELLO);
    String userId = request.context().userId();

    when(credentialsProvider.getDecryptedApiKey(userId, "anthropic"))
        .thenReturn(Optional.of("mock-key"));
    when(credentialsProvider.getDecryptedApiKey(userId, "google"))
        .thenReturn(Optional.of("mock-key"));

    ChatModel mockAnthropicModel = mock(ChatModel.class);
    ChatModel mockGoogleModel = mock(ChatModel.class);

    when(modelFactory.createChatModel("anthropic", "claude-3", "mock-key"))
        .thenReturn(mockAnthropicModel);
    when(modelFactory.createChatModel("google", "gemini-pro", "mock-key"))
        .thenReturn(mockGoogleModel);

    // Stub streams
    var mockOut = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
    when(mockOut.getText()).thenReturn("chunk");
    var response = new ChatResponse(List.of(new Generation(mockOut, ChatGenerationMetadata.NULL)));
    when(mockAnthropicModel.stream(anthropicPrompt)).thenReturn(Flux.just(response));
    when(mockGoogleModel.stream(googlePrompt)).thenReturn(Flux.just(response));

    // Test Anthropic compilation path
    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, "anthropic", List.of(interceptor)))
        .thenReturn(anthropicContext);
    streamBridge
        .createStream(
            request, orchestrationPipeline, "anthropic", List.of(interceptor), eventPublisher)
        .collectList()
        .block();

    // Test Google compilation path
    when(pipelineBridge.compileContext(
            request, orchestrationPipeline, "google", List.of(interceptor)))
        .thenReturn(googleContext);
    streamBridge
        .createStream(
            request, orchestrationPipeline, "google", List.of(interceptor), eventPublisher)
        .collectList()
        .block();

    verify(modelFactory).createChatModel("anthropic", "claude-3", "mock-key");
    verify(modelFactory).createChatModel("google", "gemini-pro", "mock-key");
  }
}
