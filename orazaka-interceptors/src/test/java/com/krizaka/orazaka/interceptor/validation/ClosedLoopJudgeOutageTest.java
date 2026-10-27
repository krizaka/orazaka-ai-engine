package com.krizaka.orazaka.interceptor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.orazaka.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.orazaka.core.domain.model.PromptContext;
import com.krizaka.orazaka.core.domain.model.ValidationPipelineConfiguration;
import com.krizaka.orazaka.core.domain.model.ValidationStepType;
import com.krizaka.orazaka.core.domain.model.control.ControlVerdictRecord;
import com.krizaka.orazaka.core.domain.model.control.JudgeOutagePolicy;
import com.krizaka.orazaka.core.domain.ports.outbound.ControlVerdictRecorder;
import com.krizaka.orazaka.core.domain.ports.outbound.ValidationPipelineRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** The judge outage policy, its circuit breaker and the streamed semantic screen (#125). */
class ClosedLoopJudgeOutageTest {

  private static final PromptContext CONTEXT =
      new PromptContext("Explain", Map.of("conversationId", "run-9"));

  private final ChatModel judge = mock(ChatModel.class);
  private final ControlVerdictRecorder recorder = mock(ControlVerdictRecorder.class);

  private static ChatResponse reply(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  private ClosedLoopValidationInterceptor interceptor(
      JudgeOutagePolicy staticPolicy, ValidationPipelineRepository repo, int breakerThreshold) {
    var props =
        new ClosedLoopValidationProperties(
            true,
            0,
            true,
            false,
            true,
            false,
            Duration.ofSeconds(2),
            breakerThreshold,
            Duration.ofMinutes(5),
            staticPolicy);
    return new ClosedLoopValidationInterceptor(
        props, judge, repo, null, recorder, null, Clock.systemUTC());
  }

  @Test
  @DisplayName("DETERMINISTIC_ONLY serves on tiers A/B with a visible badge and audits it DEGRADED")
  void degradedWithBadgeAndAudit() {
    when(judge.call(any(Prompt.class))).thenThrow(new IllegalStateException("down"));
    var interceptor = interceptor(JudgeOutagePolicy.DETERMINISTIC_ONLY, null, 3);

    var screened = interceptor.screen(CONTEXT, "{\"ok\":true}");

    assertThat(screened.text()).isEqualTo("{\"ok\":true}");
    assertThat(screened.notes())
        .containsEntry("control.verdict", "DEGRADED")
        .containsEntry("control.badge", "verification_unavailable");
    var record = ArgumentCaptor.forClass(ControlVerdictRecord.class);
    verify(recorder).record(record.capture());
    assertThat(record.getValue().actionTaken()).isEqualTo("DEGRADED");
    assertThat(record.getValue().runId()).isEqualTo("run-9");
  }

  @Test
  @DisplayName("DETERMINISTIC_ONLY still refuses an answer that fails a deterministic tier")
  void degradedStillChecksTheDeterministicTiers() {
    when(judge.call(any(Prompt.class))).thenThrow(new IllegalStateException("down"));
    var interceptor = interceptor(JudgeOutagePolicy.DETERMINISTIC_ONLY, null, 3);

    assertThatThrownBy(() -> interceptor.screen(CONTEXT, "{invalid"))
        .isInstanceOf(PipelineShortCircuitException.class)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo("control_escalation");
  }

  @Test
  @DisplayName("the admin's value on the step payload wins over the static policy")
  void adminPolicyWins() {
    when(judge.call(any(Prompt.class))).thenThrow(new IllegalStateException("down"));
    var repo = mock(ValidationPipelineRepository.class);
    when(repo.findAllOrderedByExecution())
        .thenReturn(
            List.of(
                new ValidationPipelineConfiguration(
                    UUID.randomUUID(), ValidationStepType.STRUCTURAL_A, true, 1, Map.of()),
                new ValidationPipelineConfiguration(
                    UUID.randomUUID(),
                    ValidationStepType.SEMANTIC_C,
                    true,
                    2,
                    Map.of("onJudgeOutage", "deterministic_only"))));
    var interceptor = interceptor(JudgeOutagePolicy.ESCALATE, repo, 3);

    assertThat(interceptor.screen(CONTEXT, "plain text").notes())
        .containsEntry("control.verdict", "DEGRADED");
  }

  @Test
  @DisplayName("an unknown admin value never relaxes the control: it escalates")
  void unknownPolicyEscalates() {
    assertThat(JudgeOutagePolicy.parse("whatever", JudgeOutagePolicy.DETERMINISTIC_ONLY))
        .isEqualTo(JudgeOutagePolicy.ESCALATE);
    assertThat(JudgeOutagePolicy.parse(null, JudgeOutagePolicy.DETERMINISTIC_ONLY))
        .isEqualTo(JudgeOutagePolicy.DETERMINISTIC_ONLY);
  }

  @Test
  @DisplayName("escalation on an outage is audited and typed")
  void escalationIsAudited() {
    when(judge.call(any(Prompt.class))).thenThrow(new IllegalStateException("down"));
    var interceptor = interceptor(JudgeOutagePolicy.ESCALATE, null, 3);

    assertThatThrownBy(() -> interceptor.screen(CONTEXT, "plain text"))
        .isInstanceOf(PipelineShortCircuitException.class)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo("control_verification_unavailable");
    var record = ArgumentCaptor.forClass(ControlVerdictRecord.class);
    verify(recorder).record(record.capture());
    assertThat(record.getValue().actionTaken()).isEqualTo("ESCALATED");
  }

  @Test
  @DisplayName("once the breaker is open the judge is not called any more: the policy decides")
  void openBreakerSparesTheJudge() {
    when(judge.call(any(Prompt.class))).thenThrow(new IllegalStateException("down"));
    var interceptor = interceptor(JudgeOutagePolicy.ESCALATE, null, 2);

    for (int i = 0; i < 4; i++) {
      assertThatThrownBy(() -> interceptor.screen(CONTEXT, "plain text"))
          .isInstanceOf(PipelineShortCircuitException.class);
    }

    verify(judge, times(2)).call(any(Prompt.class));
  }

  @Test
  @DisplayName("a judge that answers nothing readable is an outage, not a neutral vote")
  void unreadableScoreIsAnOutage() {
    when(judge.call(any(Prompt.class))).thenReturn(reply("I cannot say"));
    var interceptor = interceptor(JudgeOutagePolicy.ESCALATE, null, 3);

    assertThatThrownBy(() -> interceptor.screen(CONTEXT, "plain text"))
        .isInstanceOf(PipelineShortCircuitException.class)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo("control_verification_unavailable");
  }

  @Test
  @DisplayName("a streamed answer is judged on the assembled text: a Critic win refuses, no retry")
  void streamedSemanticFailureRefuses() {
    when(judge.call(any(Prompt.class)))
        .thenAnswer(
            call -> {
              String system = ((Prompt) call.getArgument(0)).getInstructions().getFirst().getText();
              return reply(
                  system.contains("ruthless")
                      ? "{\"score\": 9, \"issues\": [\"wrong\"]}"
                      : "{\"score\": 1, \"strengths\": []}");
            });
    var interceptor = interceptor(JudgeOutagePolicy.ESCALATE, null, 3);

    assertThatThrownBy(() -> interceptor.screenStreamed(CONTEXT, "a wrong answer"))
        .isInstanceOf(PipelineShortCircuitException.class)
        .extracting(e -> ((PipelineShortCircuitException) e).reason())
        .isEqualTo("control_escalation");
    verify(judge, times(2)).call(any(Prompt.class));
  }

  @Test
  @DisplayName("a streamed answer the judge accepts carries the VALIDATED verdict")
  void streamedSemanticPassIsValidated() {
    when(judge.call(any(Prompt.class))).thenReturn(reply("{\"score\": 2}"));
    var interceptor = interceptor(JudgeOutagePolicy.ESCALATE, null, 3);

    assertThat(interceptor.screenStreamed(CONTEXT, "a fine answer").notes())
        .containsEntry("control.verdict", "VALIDATED");
  }
}
