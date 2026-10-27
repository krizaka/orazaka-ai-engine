package com.krizaka.orazaka.interceptor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.orazaka.core.application.pipeline.DynamicPipelineExecutor;
import com.krizaka.orazaka.core.application.pipeline.EnginePipelineBridge;
import com.krizaka.orazaka.core.application.pipeline.EnginePipelineContext;
import com.krizaka.orazaka.core.application.pipeline.EngineStreamBridge;
import com.krizaka.orazaka.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.orazaka.core.application.routing.DynamicChatModelFactory;
import com.krizaka.orazaka.core.domain.event.ChatCompletedEvent;
import com.krizaka.orazaka.core.domain.model.PromptContext;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatResponse;
import com.krizaka.orazaka.core.domain.ports.outbound.TestShaperPort;
import com.krizaka.orazaka.core.domain.ports.outbound.UserCredentialsProvider;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Flux;

/**
 * Guard of orazaka-ai-engine#125 (ADR-092, Loi 25 art. 12.1): the control loop fails closed. A
 * streamed answer is screened before the stream ends, and a judge outage never yields a served,
 * unvalidated answer.
 */
class ClosedLoopFailClosedGuardTest {

  private static final PromptContext CONTEXT =
      new PromptContext("Give me JSON", Map.of("conversationId", "run-1", "userId", "u-1"));

  private final ChatModel streamModel = mock(ChatModel.class);
  private final EnginePipelineBridge pipelineBridge = mock(EnginePipelineBridge.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final DynamicPipelineExecutor pipeline = mock(DynamicPipelineExecutor.class);

  private static ChatResponse chunk(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  private Flux<InternalChatResponse> streamOf(
      PromptContextInterceptor interceptor, String... chunks) {
    var request = InternalChatRequest.simple("Give me JSON");
    var prompt = new Prompt("Give me JSON");
    var context =
        new EnginePipelineContext(
            "ollama", "run-1", "Give me JSON", prompt, List.of(), null, null, CONTEXT);
    var interceptors = List.of(interceptor);
    when(pipelineBridge.compileContext(request, pipeline, "ollama", interceptors))
        .thenReturn(context);
    when(streamModel.stream(prompt))
        .thenReturn(
            Flux.fromArray(
                Arrays.stream(chunks)
                    .map(ClosedLoopFailClosedGuardTest::chunk)
                    .toArray(ChatResponse[]::new)));
    var bridge =
        new EngineStreamBridge(
            streamModel,
            pipelineBridge,
            mock(UserCredentialsProvider.class),
            mock(DynamicChatModelFactory.class));
    return bridge.createStream(request, pipeline, "ollama", interceptors, events);
  }

  private static ClosedLoopValidationInterceptor deterministicTiers() {
    var props = new ClosedLoopValidationProperties(true, 0, true, true, false, false);
    return new ClosedLoopValidationInterceptor(props, null, null, null);
  }

  @Test
  @DisplayName("a streamed answer that fails Tier A is stopped before the stream ends")
  void streamedTierAFailureIsStopped() {
    Flux<InternalChatResponse> stream = streamOf(deterministicTiers(), "{inva", "lid");

    assertThatThrownBy(() -> stream.collectList().block())
        .isInstanceOf(PipelineShortCircuitException.class);
    verify(events, never()).publishEvent(any(ChatCompletedEvent.class));
  }

  @Test
  @DisplayName("a streamed answer that fails Tier B is stopped before the stream ends")
  void streamedTierBFailureIsStopped() {
    Flux<InternalChatResponse> stream =
        streamOf(deterministicTiers(), "Here:\n```json\n{oops\n", "```\n", "and more text");

    assertThatThrownBy(() -> stream.collectList().block())
        .isInstanceOf(PipelineShortCircuitException.class);
    verify(events, never()).publishEvent(any(ChatCompletedEvent.class));
  }

  @Test
  @DisplayName("a streamed answer that passes ends with a final event carrying the verdict")
  void streamedAnswerEndsWithItsVerdict() {
    List<InternalChatResponse> out =
        streamOf(deterministicTiers(), "{\"ok\":", "true}").collectList().block();

    assertThat(out).isNotEmpty();
    assertThat(out.getLast().metadata()).containsEntry("control.verdict", "VALIDATED");
    assertThat(out.getFirst().metadata()).containsEntry("control.verdict", "PENDING");
  }

  @Test
  @DisplayName("a Tier C judge outage is never Validated: the turn stops, typed")
  void judgeOutageEscalatesTierC() {
    ChatModel judge = mock(ChatModel.class);
    when(judge.call(any(Prompt.class))).thenThrow(new IllegalStateException("judge timeout"));
    var props = new ClosedLoopValidationProperties(true, 0, false, false, true, false);
    var interceptor = new ClosedLoopValidationInterceptor(props, judge, null, null);

    assertThatThrownBy(() -> interceptor.screenResponse(CONTEXT, "an unchecked answer"))
        .isInstanceOf(PipelineShortCircuitException.class)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo("control_verification_unavailable");
  }

  @Test
  @DisplayName("a Tier D outage is never Validated: the turn stops, typed")
  void judgeOutageEscalatesTierD() {
    TestShaperPort shaper = mock(TestShaperPort.class);
    when(shaper.generateAssertions(any(), any())).thenThrow(new IllegalStateException("down"));
    var props = new ClosedLoopValidationProperties(true, 0, false, false, false, true);
    var interceptor = new ClosedLoopValidationInterceptor(props, null, null, shaper);

    assertThatThrownBy(() -> interceptor.screenResponse(CONTEXT, "an unchecked answer"))
        .isInstanceOf(PipelineShortCircuitException.class)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo("control_verification_unavailable");
  }
}
