package com.krizaka.orazaka.adapter.springai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.orazaka.core.application.pipeline.EnginePipelineBridge.SpringPreparedPrompt;
import com.krizaka.orazaka.core.application.routing.DynamicChatModelFactory;
import com.krizaka.orazaka.core.domain.model.chat.ChatRoute;
import com.krizaka.orazaka.core.domain.model.chat.PreparedPrompt;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/** The Spring AI side of the chat invocation port: route, text and usage. */
class SpringAiChatInvocationTest {

  private final ChatModel platform = mock(ChatModel.class);

  @SuppressWarnings("unchecked")
  private final DynamicChatModelFactory<ChatModel> factory = mock(DynamicChatModelFactory.class);

  private final SpringAiChatInvocation invocation = new SpringAiChatInvocation(platform, factory);
  private final PreparedPrompt prompt =
      new SpringPreparedPrompt(new Prompt(List.of(new UserMessage("hi"))));

  private static ChatResponse answer(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  @Test
  void aPlatformRouteCallsThePlatformModel() {
    when(platform.call(any(Prompt.class))).thenReturn(answer("hello"));

    var out = invocation.call(prompt, ChatRoute.platform());

    assertThat(out.text()).isEqualTo("hello");
  }

  @Test
  void aUserKeyRouteBuildsTheModelOfThatKey() {
    var own = mock(ChatModel.class);
    when(factory.createChatModel("openai", null, "k")).thenReturn(own);
    when(own.call(any(Prompt.class))).thenReturn(answer("mine"));

    assertThat(invocation.call(prompt, ChatRoute.userKey("openai", "k")).text()).isEqualTo("mine");
    verify(factory).createChatModel("openai", null, "k");
  }

  @Test
  void aChunkWithoutTextIsEmptyWhenStreaming() {
    when(platform.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(List.of())));

    var chunks = invocation.stream(prompt, ChatRoute.platform()).collectList().block();

    assertThat(chunks).hasSize(1);
    assertThat(chunks.getFirst().text()).isEmpty();
  }

  @Test
  void aPromptOfAnotherKindIsRefused() {
    var foreign = mock(PreparedPrompt.class);
    assertThatThrownBy(() -> invocation.call(foreign, ChatRoute.platform()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
