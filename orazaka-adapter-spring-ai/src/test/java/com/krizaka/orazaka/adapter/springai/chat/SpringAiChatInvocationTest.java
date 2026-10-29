package com.krizaka.orazaka.adapter.springai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.orazaka.adapter.springai.prompt.SpringPreparedPrompt;
import com.krizaka.orazaka.adapter.springai.tool.ClientToolCallback;
import com.krizaka.orazaka.core.application.routing.DynamicChatModelFactory;
import com.krizaka.orazaka.core.domain.model.chat.ChatCompletion;
import com.krizaka.orazaka.core.domain.model.chat.ChatRoute;
import com.krizaka.orazaka.core.domain.model.chat.ClientTool;
import com.krizaka.orazaka.core.domain.model.chat.PreparedPrompt;
import com.krizaka.orazaka.core.domain.model.chat.ToolCall;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
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

  // ---- ADR-094 §4.1 (orazaka-ai-engine#169): Spring AI 2 removed the tool loop from the model
  // ----

  /** A server tool that counts its runs and answers with a fixed result. */
  private static final class Lookup implements ToolCallback {
    final AtomicInteger runs = new AtomicInteger();

    @Override
    public ToolDefinition getToolDefinition() {
      return ToolDefinition.builder()
          .name("lookup")
          .description("d")
          .inputSchema("{\"type\":\"object\"}")
          .build();
    }

    @Override
    public String call(String toolInput) {
      runs.incrementAndGet();
      return "\"42\"";
    }
  }

  private static ChatResponse asking(String tool) {
    var call = new AssistantMessage.ToolCall("c1", "function", tool, "{}");
    return new ChatResponse(
        List.of(
            new Generation(
                AssistantMessage.builder().content("").toolCalls(List.of(call)).build())));
  }

  private static PreparedPrompt withTools(ToolCallback... tools) {
    var options = ToolCallingChatOptions.builder().toolCallbacks(List.of(tools)).build();
    return new SpringPreparedPrompt(new Prompt(List.of(new UserMessage("what is it?")), options));
  }

  @Test
  void aServerToolTheModelAsksForIsRunOnceAndItsResultReachesTheModel() {
    var lookup = new Lookup();
    when(platform.call(any(Prompt.class))).thenReturn(asking("lookup"), answer("it is 42"));

    var out = invocation.call(withTools(lookup), ChatRoute.platform());

    assertThat(out.text()).isEqualTo("it is 42");
    assertThat(out.toolCalls()).isEmpty();
    assertThat(lookup.runs).hasValue(1);
  }

  @Test
  void aToolRunByTheEngineIsReportedOnTheCompletion() {
    var lookup = new Lookup();
    when(platform.call(any(Prompt.class))).thenReturn(asking("lookup"), answer("it is 42"));

    var out = invocation.call(withTools(lookup), ChatRoute.platform());

    assertThat(out.executed()).extracting(ToolCall::name).containsExactly("lookup");
  }

  @Test
  void aToolRunByTheEngineIsReportedWhenStreamingToo() {
    var lookup = new Lookup();
    when(platform.stream(any(Prompt.class)))
        .thenReturn(Flux.just(asking("lookup")), Flux.just(answer("it is 42")));

    var chunks = invocation.stream(withTools(lookup), ChatRoute.platform()).collectList().block();

    assertThat(chunks)
        .flatExtracting(ChatCompletion::executed)
        .extracting(ToolCall::name)
        .containsExactly("lookup");
  }

  @Test
  void aServerToolTheModelAsksForIsRunWhenStreamingToo() {
    var lookup = new Lookup();
    when(platform.stream(any(Prompt.class)))
        .thenReturn(Flux.just(asking("lookup")), Flux.just(answer("it is 42")));

    var chunks = invocation.stream(withTools(lookup), ChatRoute.platform()).collectList().block();

    assertThat(chunks).extracting(ChatCompletion::text).contains("it is 42");
    assertThat(chunks).allSatisfy(c -> assertThat(c.toolCalls()).isEmpty());
    assertThat(lookup.runs).hasValue(1);
  }

  @Test
  void aClientToolIsHandedBackAndNeverRun() {
    var client = new ClientToolCallback(new ClientTool("pick", "d", "{\"type\":\"object\"}"));
    when(platform.call(any(Prompt.class))).thenReturn(asking("pick"));

    var out = invocation.call(withTools(client), ChatRoute.platform());

    assertThat(out.toolCalls()).extracting(ToolCall::name).containsExactly("pick");
  }

  @Test
  void aModelThatKeepsAskingStopsAtTheBound() {
    var lookup = new Lookup();
    when(platform.call(any(Prompt.class))).thenReturn(asking("lookup"));

    assertThatThrownBy(() -> invocation.call(withTools(lookup), ChatRoute.platform()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("tool rounds");
    assertThat(lookup.runs.get()).isEqualTo(SpringAiChatInvocation.DEFAULT_MAX_TOOL_ROUNDS);
  }
}
