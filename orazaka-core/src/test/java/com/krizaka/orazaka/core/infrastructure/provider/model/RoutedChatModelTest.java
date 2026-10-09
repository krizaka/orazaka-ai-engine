package com.krizaka.orazaka.core.infrastructure.provider.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.orazaka.core.infrastructure.provider.DynamicModelRouter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

/**
 * Guards the ADR-030 option-normalisation contract. The universal proxy ({@code
 * UniversalProxyChatProvider}) always resolves an {@code OpenAiChatModel}, whose {@code
 * createRequest} casts the prompt's runtime options to {@link OpenAiChatOptions}. So {@link
 * RoutedChatModel} must coerce upstream {@code OllamaChatOptions} to {@code OpenAiChatOptions} —
 * without that conversion chat throws a {@code ClassCastException}, and a naive conversion NPEs on
 * the nullable {@code getToolCallbacks()} (the two regressions these tests pin down).
 */
class RoutedChatModelTest {

  @Test
  void toOpenAiPrompt_convertsOllamaOptions_andDoesNotNpeWhenToolCallbacksNull() {
    // OllamaChatOptions.getToolCallbacks() returns null (not an empty list): the regression case.
    Prompt prompt =
        new Prompt("hi", OllamaChatOptions.builder().model("llama3.2:3b").temperature(0.7).build());

    Prompt result = RoutedChatModel.toOpenAiPrompt(prompt);

    assertThat(result.getOptions()).isInstanceOf(OpenAiChatOptions.class);
    assertThat(result.getOptions().getModel()).isEqualTo("llama3.2:3b");
    assertThat(result.getOptions().getTemperature()).isEqualTo(0.7);
  }

  @Test
  void toOpenAiPrompt_preservesToolCallbacksWhenPresent() {
    ToolCallback tool = mock(ToolCallback.class);
    Prompt prompt =
        new Prompt(
            "hi", OllamaChatOptions.builder().model("m").toolCallbacks(List.of(tool)).build());

    OpenAiChatOptions result =
        (OpenAiChatOptions) RoutedChatModel.toOpenAiPrompt(prompt).getOptions();

    assertThat(result.getToolCallbacks()).containsExactly(tool);
  }

  @Test
  void toOpenAiPrompt_passesOpenAiOptionsThroughUnchanged() {
    Prompt prompt = new Prompt("hi", OpenAiChatOptions.builder().model("gpt-4o").build());

    assertThat(RoutedChatModel.toOpenAiPrompt(prompt)).isSameAs(prompt);
  }

  @Test
  void stream_routesToRequestedModel_andForwardsOpenAiOptions() {
    DynamicModelRouter router = mock(DynamicModelRouter.class);
    ChatModel delegate = mock(ChatModel.class);
    when(delegate.stream(any(Prompt.class))).thenReturn(Flux.empty());
    when(router.chatModel("llama3.2:3b")).thenReturn(delegate);
    RoutedChatModel routed = new RoutedChatModel(router);

    routed.stream(new Prompt("hi", OllamaChatOptions.builder().model("llama3.2:3b").build()))
        .blockLast();

    verify(router).chatModel("llama3.2:3b");
    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    verify(delegate).stream(captor.capture());
    assertThat(captor.getValue().getOptions()).isInstanceOf(OpenAiChatOptions.class);
  }

  @Test
  void stream_routesToDefaultModel_whenPromptHasNoOptions() {
    DynamicModelRouter router = mock(DynamicModelRouter.class);
    ChatModel delegate = mock(ChatModel.class);
    when(delegate.stream(any(Prompt.class))).thenReturn(Flux.empty());
    when(router.defaultChatModel(any())).thenReturn(delegate);
    RoutedChatModel routed = new RoutedChatModel(router);

    routed.stream(new Prompt("hi")).blockLast();

    verify(router).defaultChatModel(any());
  }

  @Test
  void call_routesToRequestedModel_andForwardsOpenAiOptions() {
    DynamicModelRouter router = mock(DynamicModelRouter.class);
    ChatModel delegate = mock(ChatModel.class);
    when(delegate.call(any(Prompt.class))).thenReturn(mock(ChatResponse.class));
    when(router.chatModel("llama3.2:3b")).thenReturn(delegate);
    RoutedChatModel routed = new RoutedChatModel(router);

    routed.call(new Prompt("hi", OllamaChatOptions.builder().model("llama3.2:3b").build()));

    ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
    verify(delegate).call(captor.capture());
    assertThat(captor.getValue().getOptions()).isInstanceOf(OpenAiChatOptions.class);
  }
}
