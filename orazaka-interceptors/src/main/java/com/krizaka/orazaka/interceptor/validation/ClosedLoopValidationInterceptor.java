package com.krizaka.orazaka.interceptor.validation;

import com.krizaka.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.orazaka.core.application.pipeline.PipelineShortCircuitException;
import com.krizaka.orazaka.core.domain.model.AssertionContract;
import com.krizaka.orazaka.core.domain.model.PromptContext;
import com.krizaka.orazaka.core.domain.model.ValidationPipelineConfiguration;
import com.krizaka.orazaka.core.domain.model.ValidationStepType;
import com.krizaka.orazaka.core.domain.model.control.ControlVerdict;
import com.krizaka.orazaka.core.domain.model.control.ControlVerdictRecord;
import com.krizaka.orazaka.core.domain.model.control.DriftAdjustment;
import com.krizaka.orazaka.core.domain.model.control.HumanEscalation;
import com.krizaka.orazaka.core.domain.model.control.Validated;
import com.krizaka.orazaka.core.domain.ports.outbound.CodeSandboxPort;
import com.krizaka.orazaka.core.domain.ports.outbound.ControlVerdictRecorder;
import com.krizaka.orazaka.core.domain.ports.outbound.TestShaperPort;
import com.krizaka.orazaka.core.domain.ports.outbound.ValidationPipelineRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Autonomous multi-tier closed-loop self-correction advisor for the Satsui Engine.
 *
 * <p>Implements a configurable 4-tier validation chain applied <strong>post-inference</strong>:
 *
 * <ul>
 *   <li><strong>Tier A — Deterministic JSON Schema (0-token cost)</strong>: Validates the LLM
 *       response against Jackson schema rules. Forces an instant structured retry if the response
 *       fails parsing. Pure structural check — zero inference tokens consumed.
 *   <li><strong>Tier B — MCP Sandbox Crash-Test</strong>: Extracts code blocks from the response
 *       and delegates compilation/linting to an isolated environment via the MCP client.
 *       Synthesizes feedback from sandbox errors and retries with corrective context.
 *   <li><strong>Tier C — Semantic Consensus Debate</strong>: Spawns temperature-0.0 Critic vs
 *       Advocate personas to judge semantic alignment against user intent. If the Critic score
 *       outweighs the Advocate, triggers a retry with the synthesized critique.
 *   <li><strong>Tier D — Test-Driven Response (TDR)</strong>: Pre-generates test assertion schemas
 *       via a fast reasoning model and validates the LLM response against them. If Tier D fails but
 *       Tiers A and B pass, gracefully delegates to Tier C for final arbitration.
 * </ul>
 *
 * <p><strong>Control loop (ADR-092, orazaka-ai-engine#116)</strong>: the interceptor decides in
 * {@link #screenResponse}, before an answer is served. Each pass ends in a typed {@link
 * ControlVerdict}: {@link Validated} serves the text; {@link DriftAdjustment} re-runs the step with
 * the diff of what failed injected (so the next attempt never receives the input the failed one
 * did); once the retries are spent, or when nothing can adjust, {@link HumanEscalation} refuses the
 * turn with a {@link PipelineShortCircuitException} — an output that did not pass is never served.
 * Every adjustment and escalation is recorded through the {@link ControlVerdictRecorder} (the
 * outbox). Streamed answers leave chunk by chunk before the end and are not screened here (see
 * orazaka-ai-engine#114).
 *
 * <p>Tier execution order and enabled state are loaded dynamically from the {@link
 * ValidationPipelineRepository} database configuration. If the repository is unavailable, falls
 * back to the static {@link ClosedLoopValidationProperties} configuration.
 *
 * @since 1.0.0
 */
public class ClosedLoopValidationInterceptor implements PromptContextInterceptor {

  private static final Logger logger =
      LoggerFactory.getLogger(ClosedLoopValidationInterceptor.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String CRITIC_SYSTEM =
      "You are a ruthless code and logic critic. Analyze the following AI response for factual"
          + " errors, logical inconsistencies, missing edge cases, and alignment violations against"
          + " the original user intent. Score from 0 (perfect) to 10 (critically flawed). Respond"
          + " with ONLY a JSON object: {\"score\": N, \"issues\": [\"...\"]}.";

  private static final String ADVOCATE_SYSTEM =
      "You are a constructive advocate defending the following AI response. Evaluate whether it"
          + " faithfully addresses the user's original intent, is structurally sound, and provides"
          + " actionable value. Score from 0 (no value) to 10 (excellent). Respond with ONLY a JSON"
          + " object: {\"score\": N, \"strengths\": [\"...\"]}.";

  /** The machine-readable refusal code of a {@link HumanEscalation}. */
  public static final String ESCALATION_REASON = "control_escalation";

  private static final Pattern CODE_FENCE =
      Pattern.compile("```([\\w+#.-]*)[ \\t]*\\R(.*?)```", Pattern.DOTALL);
  private static final int MAX_REASON_LENGTH = 500;

  private static final String ADJUST_SYSTEM =
      "You are correcting your own previous answer. A check rejected it. Return ONLY the corrected"
          + " answer, in the same format as asked, with no commentary.";

  private final ClosedLoopValidationProperties properties;
  private final ChatModel chatModel;
  private final ValidationPipelineRepository validationPipelineRepository;
  private final TestShaperPort testShaperPort;
  private final ControlVerdictRecorder verdictRecorder;
  private final CodeSandboxPort sandbox;
  private final Clock clock;

  /**
   * Constructs a ClosedLoopValidationInterceptor with full 4-tier support and no audit recorder.
   *
   * @param properties Configuration controlling which tiers are active (static fallback).
   * @param chatModel ChatModel for Tier C debate and for adjusting a rejected answer (nullable —
   *     Tier C degrades gracefully, a rejected answer then escalates at once).
   * @param validationPipelineRepository Repository for dynamic tier configuration (nullable —
   *     degrades to static config).
   * @param testShaperPort Tier D assertion generator (nullable — Tier D degrades gracefully).
   */
  public ClosedLoopValidationInterceptor(
      ClosedLoopValidationProperties properties,
      ChatModel chatModel,
      ValidationPipelineRepository validationPipelineRepository,
      TestShaperPort testShaperPort) {
    this(
        properties,
        chatModel,
        validationPipelineRepository,
        testShaperPort,
        null,
        null,
        Clock.systemUTC());
  }

  /**
   * Constructs the full control loop.
   *
   * @param properties Configuration controlling which tiers are active (static fallback).
   * @param chatModel ChatModel for Tier C and for adjusting a rejected answer (nullable).
   * @param validationPipelineRepository Repository for dynamic tier configuration (nullable).
   * @param testShaperPort Tier D assertion generator (nullable).
   * @param verdictRecorder Where adjustments and escalations are persisted (nullable — not
   *     persisted).
   * @param sandbox Tier B sandbox adding to the built-in structural crash-test (nullable).
   * @param clock The clock stamping the persisted verdicts.
   */
  public ClosedLoopValidationInterceptor(
      ClosedLoopValidationProperties properties,
      ChatModel chatModel,
      ValidationPipelineRepository validationPipelineRepository,
      TestShaperPort testShaperPort,
      ControlVerdictRecorder verdictRecorder,
      CodeSandboxPort sandbox,
      Clock clock) {
    this.properties =
        Objects.requireNonNull(properties, "ClosedLoopValidationProperties must not be null");
    this.chatModel = chatModel;
    this.validationPipelineRepository = validationPipelineRepository;
    this.testShaperPort = testShaperPort;
    this.verdictRecorder = verdictRecorder;
    this.sandbox = sandbox;
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /** A control: whatever it did not anticipate stops the turn instead of serving it unchecked. */
  @Override
  public boolean failsClosed() {
    return true;
  }

  @Override
  public boolean isAiDependent() {
    return (properties.debateEnabled() && chatModel != null)
        || (properties.tdrEnabled() && testShaperPort != null);
  }

  @Override
  public String screenResponse(PromptContext context, String responseText) {
    if (!properties.enabled() || responseText == null || responseText.isBlank()) {
      return responseText;
    }

    List<ValidationStepType> activeSteps = resolveActiveSteps();
    if (activeSteps.isEmpty()) {
      logger.debug("Closed-loop validation — No active tiers resolved. Skipping validation.");
      return responseText;
    }

    String userPrompt = userPromptOf(context);
    String runId = runIdOf(context);
    String current = responseText;

    for (int attempt = 0; ; attempt++) {
      Failure failure = firstFailure(userPrompt, current, activeSteps);
      if (failure == null) {
        ControlVerdict validated = new Validated(activeSteps);
        logger.debug("Closed-loop validation — {} on attempt {}.", validated.reason(), attempt + 1);
        return current;
      }
      logger.warn(
          "Tier {} — attempt {}/{}: {}",
          failure.step().name(),
          attempt + 1,
          properties.maxRetries() + 1,
          failure.issue());

      String adjusted = null;
      if (attempt < properties.maxRetries()) {
        adjusted = adjust(userPrompt, current, failure);
      }
      if (adjusted == null) {
        ControlVerdict escalation =
            new HumanEscalation(failure.step(), failure.summary(), failure.issue(), attempt + 1);
        record(runId, escalation);
        throw new PipelineShortCircuitException(
            getId(),
            ESCALATION_REASON,
            "The answer could not be validated (" + failure.step().name() + ") and was not served.",
            null);
      }
      record(
          runId, new DriftAdjustment(failure.step(), failure.summary(), failure.issue(), attempt));
      current = adjusted;
    }
  }

  /** The first failing tier, in order, or {@code null} when every active tier passes. */
  private Failure firstFailure(
      String promptText, String candidate, List<ValidationStepType> activeSteps) {
    boolean tierAPass = true;
    boolean tierBPass = true;

    for (ValidationStepType step : activeSteps) {
      Optional<String> issue =
          switch (step) {
            case STRUCTURAL_A -> {
              Optional<String> result = executeTierA(candidate);
              tierAPass = result.isEmpty();
              yield result;
            }
            case SANDBOX_B -> {
              Optional<String> result = executeTierB(candidate);
              tierBPass = result.isEmpty();
              yield result;
            }
            case SEMANTIC_C -> executeTierC(promptText, candidate);
            case TDR_D -> executeTierD(promptText, candidate, tierAPass, tierBPass);
          };
      if (issue.isPresent()) {
        return new Failure(step, issue.get());
      }
    }
    return null;
  }

  /**
   * Re-runs the step with the diff of what failed injected. The prompt of the new attempt carries
   * the previous answer and the diff, so it differs from the one that failed.
   *
   * @return the adjusted answer, or {@code null} when nothing can adjust it (no model, a failure,
   *     an empty answer)
   */
  private String adjust(String userPrompt, String previous, Failure failure) {
    if (chatModel == null) {
      return null;
    }
    try {
      String instruction =
          "Original request:\n"
              + userPrompt
              + "\n\nYour previous answer:\n"
              + previous
              + "\n\nIt failed the "
              + failure.step().name()
              + " check:\n"
              + failure.issue()
              + "\n\nCorrect it.";
      ChatResponse response =
          chatModel.call(
              new Prompt(List.of(new SystemMessage(ADJUST_SYSTEM), new UserMessage(instruction))));
      String text =
          Optional.ofNullable(response)
              .map(ChatResponse::getResult)
              .map(Generation::getOutput)
              .map(AssistantMessage::getText)
              .orElse(null);
      return text == null || text.isBlank() ? null : text;
    } catch (RuntimeException e) {
      logger.error("Closed-loop validation — the adjustment attempt failed; escalating.", e);
      return null;
    }
  }

  private void record(String runId, ControlVerdict verdict) {
    if (verdictRecorder != null) {
      verdictRecorder.record(ControlVerdictRecord.of(runId, verdict, clock.instant()));
    }
  }

  private static String userPromptOf(PromptContext context) {
    if (context == null) {
      return "";
    }
    String refined = context.refinedPrompt();
    return refined != null ? refined : String.valueOf(context.rawUserQuery());
  }

  private static String runIdOf(PromptContext context) {
    if (context != null) {
      for (String key : List.of("runId", "conversationId")) {
        Object value = context.userMetadata().get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
          return String.valueOf(value);
        }
      }
    }
    return "unknown";
  }

  /** A tier that rejected a candidate, and why. */
  private record Failure(ValidationStepType step, String issue) {
    String summary() {
      String text = step.name() + ": " + issue;
      return text.length() > MAX_REASON_LENGTH ? text.substring(0, MAX_REASON_LENGTH) : text;
    }
  }

  /**
   * Resolves the ordered list of active validation steps. Prefers database configuration; falls
   * back to static properties if the repository is unavailable.
   */
  private List<ValidationStepType> resolveActiveSteps() {
    if (validationPipelineRepository != null) {
      try {
        List<ValidationPipelineConfiguration> dbConfigs =
            validationPipelineRepository.findAllOrderedByExecution();
        if (!dbConfigs.isEmpty()) {
          return dbConfigs.stream()
              .filter(ValidationPipelineConfiguration::enabled)
              .map(ValidationPipelineConfiguration::stepType)
              .toList();
        }
      } catch (RuntimeException e) {
        logger.warn("Failed to load validation pipeline from database — using static config.", e);
      }
    }

    // Static fallback from properties
    List<ValidationStepType> steps = new ArrayList<>();
    if (properties.schemaStrict()) {
      steps.add(ValidationStepType.STRUCTURAL_A);
    }
    if (properties.sandboxEnabled()) {
      steps.add(ValidationStepType.SANDBOX_B);
    }
    if (properties.debateEnabled()) {
      steps.add(ValidationStepType.SEMANTIC_C);
    }
    if (properties.tdrEnabled()) {
      steps.add(ValidationStepType.TDR_D);
    }
    return List.copyOf(steps);
  }

  /**
   * Tier B — crash-test of the code blocks. Every fenced block must be closed; a {@code json} block
   * must parse; a wired {@link CodeSandboxPort} compiles or lints each block as well. An answer
   * with no code block has nothing to crash-test. A sandbox that itself fails rejects the answer
   * (fails closed).
   *
   * @param responseText The LLM response text.
   * @return Empty if every block passes; the first failure otherwise.
   */
  private Optional<String> executeTierB(String responseText) {
    if (responseText.split("```", -1).length % 2 == 0) {
      return Optional.of("Unterminated code block: a ``` fence is never closed.");
    }
    Matcher blocks = CODE_FENCE.matcher(responseText);
    while (blocks.find()) {
      String language = blocks.group(1).toLowerCase(java.util.Locale.ROOT);
      String code = blocks.group(2);
      if ("json".equals(language)) {
        try {
          MAPPER.readTree(code);
        } catch (Exception e) {
          return Optional.of("Invalid JSON in a json code block: " + e.getMessage());
        }
      }
      if (sandbox != null) {
        try {
          Optional<String> failure = sandbox.crashTest(language, code);
          if (failure.isPresent()) {
            return Optional.of("Sandbox crash-test failed (" + language + "): " + failure.get());
          }
        } catch (RuntimeException e) {
          return Optional.of("Sandbox crash-test could not run: " + e.getMessage());
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Tier A — Deterministic JSON Schema validation.
   *
   * <p>Attempts to parse the response as JSON. If the response is not valid JSON and strict mode is
   * enabled, returns a description of the parse failure. Zero token cost — pure Jackson parse.
   *
   * @param responseText The LLM response text.
   * @return Empty if valid or non-strict; description of the issue otherwise.
   */
  private Optional<String> executeTierA(String responseText) {
    if (!properties.schemaStrict()) {
      return Optional.empty();
    }

    // Attempt to extract JSON from the response (may be embedded in markdown fences)
    String jsonCandidate = extractJsonBlock(responseText);
    if (jsonCandidate == null) {
      return Optional.empty();
    }

    try {
      MAPPER.readTree(jsonCandidate);
      return Optional.empty();
    } catch (Exception e) {
      return Optional.of("Invalid JSON structure: " + e.getMessage());
    }
  }

  /**
   * Tier C — Semantic Consensus Debate.
   *
   * <p>Spawns two temperature-0.0 LLM calls: a Critic and an Advocate. If the Critic's score
   * exceeds the Advocate's, the response is deemed misaligned.
   *
   * @param userPrompt The original user prompt for intent alignment.
   * @param responseText The LLM response to evaluate.
   * @return Empty if Advocate prevails; critique summary if Critic prevails.
   */
  private Optional<String> executeTierC(String userPrompt, String responseText) {
    if (!properties.debateEnabled() || chatModel == null) {
      return Optional.empty();
    }

    try {
      String debateContext =
          "Original user request: " + userPrompt + "\n\nAI Response:\n" + responseText;

      ChatOptions debateOptions = ChatOptions.builder().temperature(0.0).build();

      // Critic evaluation
      ChatResponse criticResponse =
          chatModel.call(
              new Prompt(
                  List.of(new SystemMessage(CRITIC_SYSTEM), new UserMessage(debateContext)),
                  debateOptions));

      // Advocate evaluation
      ChatResponse advocateResponse =
          chatModel.call(
              new Prompt(
                  List.of(new SystemMessage(ADVOCATE_SYSTEM), new UserMessage(debateContext)),
                  debateOptions));

      int criticScore = extractScore(criticResponse);
      int advocateScore = extractScore(advocateResponse);

      logger.debug(
          "Tier C Debate — Critic: {}, Advocate: {} (Critic wins if Critic > Advocate)",
          criticScore,
          advocateScore);

      if (criticScore > advocateScore) {
        String criticText =
            Optional.ofNullable(criticResponse)
                .map(ChatResponse::getResult)
                .map(Generation::getOutput)
                .map(AssistantMessage::getText)
                .orElse("Critic prevailed with no detailed feedback.");
        return Optional.of(criticText);
      }

      return Optional.empty();
    } catch (RuntimeException e) {
      logger.error("Tier C debate failed — gracefully degrading.", e);
      return Optional.empty();
    }
  }

  /**
   * Tier D — Test-Driven Response (TDR) validation.
   *
   * <p>Generates assertion contracts via the {@link TestShaperPort} and evaluates them against the
   * response. If Tier D assertions fail but Tiers A and B passed, gracefully delegates to Tier C
   * for final arbitration instead of immediate rejection.
   *
   * @param userPrompt The original user prompt.
   * @param responseText The LLM response to validate.
   * @param tierAPass Whether Tier A (structural) passed.
   * @param tierBPass Whether Tier B (sandbox) passed.
   * @return Empty if assertions pass or TDR is unavailable; description of failures otherwise.
   */
  private Optional<String> executeTierD(
      String userPrompt, String responseText, boolean tierAPass, boolean tierBPass) {
    if (!properties.tdrEnabled() || testShaperPort == null) {
      return Optional.empty();
    }

    try {
      AssertionContract contract = testShaperPort.generateAssertions(userPrompt, responseText);
      if (!contract.hasAssertions()) {
        logger.debug("Tier D — No assertions generated. Passing by default.");
        return Optional.empty();
      }

      // Evaluate assertions against the response
      List<String> failures =
          contract.assertions().stream()
              .filter(assertion -> !evaluateAssertion(assertion, responseText))
              .toList();

      if (failures.isEmpty()) {
        logger.debug(
            "Tier D — All {} assertions passed for schema '{}'.",
            contract.assertions().size(),
            contract.schemaName());
        return Optional.empty();
      }

      // Graceful degradation: if A and B passed but D failed, delegate to C
      if (tierAPass && tierBPass && properties.debateEnabled() && chatModel != null) {
        logger.info(
            "Tier D — {} assertion(s) failed but Tiers A+B passed. "
                + "Delegating to Tier C for final arbitration.",
            failures.size());
        Optional<String> tierCResult = executeTierC(userPrompt, responseText);
        if (tierCResult.isEmpty()) {
          logger.info("Tier D → C delegation: Advocate prevailed. Accepting response.");
          return Optional.empty();
        }
        return tierCResult;
      }

      return Optional.of(
          "TDR assertion failures [" + contract.schemaName() + "]: " + String.join("; ", failures));
    } catch (RuntimeException e) {
      logger.error("Tier D — TDR validation failed. Gracefully degrading.", e);
      return Optional.empty();
    }
  }

  /**
   * Evaluates a single text-based assertion against the response. Uses simple keyword/phrase
   * matching. Returns true if the assertion is considered satisfied.
   */
  private boolean evaluateAssertion(String assertion, String responseText) {
    String lowerAssertion = assertion.toLowerCase();
    String lowerResponse = responseText.toLowerCase();

    // "must contain X" pattern
    if (lowerAssertion.contains("must contain")) {
      String target = extractTarget(lowerAssertion, "must contain");
      return target != null && lowerResponse.contains(target);
    }

    // "must not contain X" pattern
    if (lowerAssertion.contains("must not contain")) {
      String target = extractTarget(lowerAssertion, "must not contain");
      return target == null || !lowerResponse.contains(target);
    }

    // Default: soft pass for unrecognized assertion patterns
    return true;
  }

  /**
   * Extracts the target phrase after a given marker in an assertion string.
   *
   * @param assertion The assertion text (lowercase).
   * @param marker The marker to search after (e.g., "must contain").
   * @return The trimmed target phrase, or null if extraction fails.
   */
  private String extractTarget(String assertion, String marker) {
    int idx = assertion.indexOf(marker);
    if (idx == -1) {
      return null;
    }
    String tail = assertion.substring(idx + marker.length()).trim();
    // Strip leading articles
    if (tail.startsWith("a ")) {
      tail = tail.substring(2);
    }
    if (tail.startsWith("an ")) {
      tail = tail.substring(3);
    }
    return tail.isBlank() ? null : tail;
  }

  private String extractJsonBlock(String text) {
    return JsonBlocks.extract(text, true);
  }

  /**
   * Extracts the numeric "score" field from a debate response JSON.
   *
   * @param response The ChatResponse from a Critic or Advocate call.
   * @return The score value, or 5 (neutral) if extraction fails.
   */
  private int extractScore(ChatResponse response) {
    try {
      String text =
          Optional.ofNullable(response)
              .map(ChatResponse::getResult)
              .map(Generation::getOutput)
              .map(AssistantMessage::getText)
              .orElse(null);
      if (text == null) {
        return 5;
      }

      String json = extractJsonBlock(text);
      if (json == null) {
        return 5;
      }

      JsonNode node = MAPPER.readTree(json);
      return node.has("score") ? node.get("score").asInt(5) : 5;
    } catch (Exception e) {
      return 5;
    }
  }
}
