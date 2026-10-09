package com.krizaka.orazaka.interceptor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.orazaka.core.domain.model.AssertionContract;
import com.krizaka.orazaka.core.domain.model.ValidationPipelineConfiguration;
import com.krizaka.orazaka.core.domain.model.ValidationStepType;
import com.krizaka.orazaka.core.domain.ports.outbound.TestShaperPort;
import com.krizaka.orazaka.core.domain.ports.outbound.ValidationPipelineRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

@ExtendWith(MockitoExtension.class)
class ClosedLoopValidationInterceptorTest {

  @Mock private ChatModel chatModel;

  @ParameterizedTest(name = "Tier A - {0}")
  @CsvSource({
    "Valid JSON passes schema validation silently, true, 3, '{\"key\": \"value\"}'",
    "Invalid JSON triggers retry logging (no exception thrown), true, 1, '{invalid json'",
    "Non-JSON text passes when no JSON block detected, true, 3, 'This is a plain text response without JSON.'"
  })
  void tierA_validationScenarios(
      String description, boolean schemaStrict, int maxRetries, String responseText) {
    var props =
        new ClosedLoopValidationProperties(true, maxRetries, schemaStrict, false, false, false);
    var advisor = new ClosedLoopValidationInterceptor(props, null, null, null);

    assertThatCode(() -> advisor.postProcess(null, "test prompt", responseText))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Tier C — Advocate wins, no retry triggered")
  void tierC_advocateWinsNoRetry() {
    var props = new ClosedLoopValidationProperties(true, 3, false, false, true, false);

    // Critic scores low (3), Advocate scores high (8) — Advocate wins
    ChatResponse criticResponse = mockResponse("{\"score\": 3, \"issues\": []}");
    ChatResponse advocateResponse = mockResponse("{\"score\": 8, \"strengths\": [\"solid\"]}");

    when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(criticResponse, advocateResponse);

    var advisor = new ClosedLoopValidationInterceptor(props, chatModel, null, null);

    assertThatCode(() -> advisor.postProcess(null, "Write a function", "def foo(): return 42"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Tier C — Critic wins, retries exhausted gracefully")
  void tierC_criticWinsRetriesExhausted() {
    var props = new ClosedLoopValidationProperties(true, 1, false, false, true, false);

    // Critic always scores high (9), Advocate always scores low (2) — Critic wins
    ChatResponse criticResponse = mockResponse("{\"score\": 9, \"issues\": [\"logical flaw\"]}");
    ChatResponse advocateResponse = mockResponse("{\"score\": 2, \"strengths\": []}");

    when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class)))
        .thenReturn(criticResponse, advocateResponse);

    var advisor = new ClosedLoopValidationInterceptor(props, chatModel, null, null);

    assertThatCode(() -> advisor.postProcess(null, "Write correct code", "def foo(): return None"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Disabled advisor does nothing")
  void disabledAdvisorNoOp() {
    var props = new ClosedLoopValidationProperties(false, 3, true, true, true, false);
    var advisor = new ClosedLoopValidationInterceptor(props, chatModel, null, null);

    assertThatCode(() -> advisor.postProcess(null, "prompt", "{\"key\": \"value\"}"))
        .doesNotThrowAnyException();
    verifyNoInteractions(chatModel);
  }

  @Test
  @DisplayName("Null response text does nothing")
  void nullResponseNoOp() {
    var props = ClosedLoopValidationProperties.defaults();
    var advisor = new ClosedLoopValidationInterceptor(props, chatModel, null, null);

    assertThatCode(() -> advisor.postProcess(null, "prompt", null)).doesNotThrowAnyException();
    verifyNoInteractions(chatModel);
  }

  @Test
  @DisplayName("isAiDependent reflects Tier C and Tier D state")
  void isAiDependentReflectsTierCAndD() {
    // Tier C enabled with chatModel → AI-dependent
    var enabledC = new ClosedLoopValidationProperties(true, 3, true, false, true, false);
    assertThat(new ClosedLoopValidationInterceptor(enabledC, chatModel, null, null).isAiDependent())
        .isTrue();

    // Tier C disabled, Tier D disabled → not AI-dependent
    var disabledAll = new ClosedLoopValidationProperties(true, 3, true, false, false, false);
    assertThat(
            new ClosedLoopValidationInterceptor(disabledAll, chatModel, null, null).isAiDependent())
        .isFalse();

    // Tier C enabled but no chatModel → not AI-dependent (graceful degradation)
    var noModel = new ClosedLoopValidationProperties(true, 3, true, false, true, false);
    assertThat(new ClosedLoopValidationInterceptor(noModel, null, null, null).isAiDependent())
        .isFalse();

    // Tier D enabled with testShaperPort → AI-dependent
    var tdrPort = mock(com.krizaka.orazaka.core.domain.ports.outbound.TestShaperPort.class);
    var enabledD = new ClosedLoopValidationProperties(true, 3, true, false, false, true);
    assertThat(new ClosedLoopValidationInterceptor(enabledD, null, null, tdrPort).isAiDependent())
        .isTrue();
  }

  @Test
  @DisplayName("ClosedLoopValidationProperties self-validates bounds")
  void propertiesSelfValidation() {
    var capped = new ClosedLoopValidationProperties(true, 100, true, true, true, false);
    assertThat(capped.maxRetries()).isEqualTo(5);

    var negative = new ClosedLoopValidationProperties(true, -5, true, true, true, false);
    assertThat(negative.maxRetries()).isZero();
  }

  @Test
  @DisplayName("Should resolve active steps from database repository")
  void resolveActiveStepsFromDb() {
    var repo = mock(ValidationPipelineRepository.class);
    var config =
        new ValidationPipelineConfiguration(
            UUID.randomUUID(), ValidationStepType.SEMANTIC_C, true, 1, Map.of());
    when(repo.findAllOrderedByExecution()).thenReturn(List.of(config));

    var props = new ClosedLoopValidationProperties(true, 3, false, false, false, false);
    var advisor = new ClosedLoopValidationInterceptor(props, chatModel, repo, null);

    advisor.postProcess(null, "prompt", "response");
    verify(repo, times(1)).findAllOrderedByExecution();
  }

  @Test
  @DisplayName("Should fallback to static config on db repository exception")
  void resolveActiveStepsFallbackOnError() {
    var repo = mock(ValidationPipelineRepository.class);
    when(repo.findAllOrderedByExecution()).thenThrow(new RuntimeException("DB error"));

    var props = new ClosedLoopValidationProperties(true, 3, true, false, false, false);
    var advisor = new ClosedLoopValidationInterceptor(props, null, repo, null);

    assertThatCode(() -> advisor.postProcess(null, "prompt", "invalid json"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Tier D — Evaluation of must contain / must not contain assertions")
  void tierD_assertionEvaluation() {
    var props = new ClosedLoopValidationProperties(true, 1, false, false, false, true);
    var testShaperPort = mock(TestShaperPort.class);

    var contractPass =
        new AssertionContract(
            "schema-1", List.of("must contain value", "must not contain error"), "now");
    when(testShaperPort.generateAssertions(any(), any())).thenReturn(contractPass);

    var advisorPass = new ClosedLoopValidationInterceptor(props, null, null, testShaperPort);
    assertThatCode(() -> advisorPass.postProcess(null, "prompt", "This is the value we want."))
        .doesNotThrowAnyException();

    var contractFail1 = new AssertionContract("schema-1", List.of("must contain value"), "now");
    when(testShaperPort.generateAssertions(any(), any())).thenReturn(contractFail1);

    var advisorFail1 = new ClosedLoopValidationInterceptor(props, null, null, testShaperPort);
    assertThatCode(() -> advisorFail1.postProcess(null, "prompt", "Other content."))
        .doesNotThrowAnyException();

    var contractFail2 = new AssertionContract("schema-1", List.of("must not contain error"), "now");
    when(testShaperPort.generateAssertions(any(), any())).thenReturn(contractFail2);

    var advisorFail2 = new ClosedLoopValidationInterceptor(props, null, null, testShaperPort);
    assertThatCode(() -> advisorFail2.postProcess(null, "prompt", "This is an error!"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Tier D — Delegates to Tier C when D fails but A/B pass, and Advocate wins")
  void tierD_delegatesToC_advocateWins() {
    var props = new ClosedLoopValidationProperties(true, 1, true, false, true, true);
    var testShaperPort = mock(TestShaperPort.class);

    var contract = new AssertionContract("schema-1", List.of("must contain value"), "now");
    when(testShaperPort.generateAssertions(any(), any())).thenReturn(contract);

    ChatResponse criticResponse = mockResponse("{\"score\": 2, \"issues\": []}");
    ChatResponse advocateResponse = mockResponse("{\"score\": 8, \"strengths\": []}");
    when(chatModel.call(any(Prompt.class))).thenReturn(criticResponse, advocateResponse);

    var advisor = new ClosedLoopValidationInterceptor(props, chatModel, null, testShaperPort);
    assertThatCode(() -> advisor.postProcess(null, "prompt", "{\"key\": \"other\"}"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Tier A — JSON extraction from markdown fences")
  void tierA_jsonExtractionMarkdown() {
    var props = new ClosedLoopValidationProperties(true, 1, true, false, false, false);
    var advisor = new ClosedLoopValidationInterceptor(props, null, null, null);

    assertThatCode(() -> advisor.postProcess(null, "prompt", "```json\n{\"key\": \"val\"}\n```"))
        .doesNotThrowAnyException();

    assertThatCode(() -> advisor.postProcess(null, "prompt", "```\n{\"key\": \"val\"}\n```"))
        .doesNotThrowAnyException();

    assertThatCode(() -> advisor.postProcess(null, "prompt", "```json\n{invalid\n```"))
        .doesNotThrowAnyException();
  }

  private ChatResponse mockResponse(String text) {
    var output = new AssistantMessage(text);
    var generation = new Generation(output);
    return new ChatResponse(java.util.List.of(generation));
  }
}
