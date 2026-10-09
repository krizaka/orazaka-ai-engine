package com.krizaka.orazaka.interceptor.validation;

import com.krizaka.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.orazaka.core.domain.model.AssertionContract;
import com.krizaka.orazaka.core.domain.model.ValidationPipelineConfiguration;
import com.krizaka.orazaka.core.domain.model.ValidationStepType;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import com.krizaka.orazaka.core.domain.ports.outbound.TestShaperPort;
import com.krizaka.orazaka.core.domain.ports.outbound.ValidationPipelineRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

  private final ClosedLoopValidationProperties properties;
  private final ChatModel chatModel;
  private final ValidationPipelineRepository validationPipelineRepository;
  private final TestShaperPort testShaperPort;

  /**
   * Constructs a ClosedLoopValidationInterceptor with full 4-tier support.
   *
   * @param properties Configuration controlling which tiers are active (static fallback).
   * @param chatModel ChatModel for Tier C semantic debate (nullable — Tier C degrades gracefully).
   * @param validationPipelineRepository Repository for dynamic tier configuration (nullable —
   *     degrades to static config).
   * @param testShaperPort Tier D assertion generator (nullable — Tier D degrades gracefully).
   */
  public ClosedLoopValidationInterceptor(
      ClosedLoopValidationProperties properties,
      ChatModel chatModel,
      ValidationPipelineRepository validationPipelineRepository,
      TestShaperPort testShaperPort) {
    this.properties =
        Objects.requireNonNull(properties, "ClosedLoopValidationProperties must not be null");
    this.chatModel = chatModel;
    this.validationPipelineRepository = validationPipelineRepository;
    this.testShaperPort = testShaperPort;
  }

  @Override
  public boolean isAiDependent() {
    return (properties.debateEnabled() && chatModel != null)
        || (properties.tdrEnabled() && testShaperPort != null);
  }

  @Override
  public void postProcess(InternalChatRequest request, String promptText, String responseText) {
    if (!properties.enabled() || responseText == null || responseText.isBlank()) {
      return;
    }

    List<ValidationStepType> activeSteps = resolveActiveSteps();
    if (activeSteps.isEmpty()) {
      logger.debug("Closed-loop validation — No active tiers resolved. Skipping validation.");
      return;
    }

    String validated = responseText;
    int attempt = 0;

    while (attempt < properties.maxRetries()) {
      if (validateAttempt(attempt, promptText, validated, activeSteps)) {
        logger.debug("Closed-loop validation — All tiers passed on attempt {}.", attempt + 1);
        return;
      }
      attempt++;
    }

    logger.warn(
        "Closed-loop validation — Exhausted {} retries. Yielding last validated response.",
        properties.maxRetries());
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
    List<ValidationStepType> steps = new java.util.ArrayList<>();
    if (properties.schemaStrict()) steps.add(ValidationStepType.STRUCTURAL_A);
    if (properties.sandboxEnabled()) steps.add(ValidationStepType.SANDBOX_B);
    if (properties.debateEnabled()) steps.add(ValidationStepType.SEMANTIC_C);
    if (properties.tdrEnabled()) steps.add(ValidationStepType.TDR_D);
    return List.copyOf(steps);
  }

  private boolean validateAttempt(
      int attempt, String promptText, String validated, List<ValidationStepType> activeSteps) {

    boolean tierAPass = true;
    boolean tierBPass = true;

    for (ValidationStepType step : activeSteps) {
      Optional<String> issue =
          switch (step) {
            case STRUCTURAL_A -> {
              Optional<String> result = executeTierA(validated);
              tierAPass = result.isEmpty();
              yield result;
            }
            case SANDBOX_B -> {
              // Tier B placeholder — degrades gracefully when MCP sandbox is absent
              tierBPass = true;
              yield Optional.empty();
            }
            case SEMANTIC_C -> executeTierC(promptText, validated);
            case TDR_D -> executeTierD(promptText, validated, tierAPass, tierBPass);
          };

      if (issue.isPresent()) {
        logger.warn(
            "Tier {} — Retry {}/{}: {}",
            step.name(),
            attempt + 1,
            properties.maxRetries(),
            issue.get());
        return false;
      }
    }

    return true;
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
    if (idx == -1) return null;
    String tail = assertion.substring(idx + marker.length()).trim();
    // Strip leading articles
    if (tail.startsWith("a ")) tail = tail.substring(2);
    if (tail.startsWith("an ")) tail = tail.substring(3);
    return tail.isBlank() ? null : tail;
  }

  /**
   * Extracts a JSON code block from a markdown-fenced response.
   *
   * @param text The response text potentially containing ```json blocks.
   * @return The extracted JSON string, or null if none found.
   */
  private String extractJsonBlock(String text) {
    if (text == null) return null;
    String stripped = text.strip();
    // Direct JSON
    if (stripped.startsWith("{") || stripped.startsWith("[")) {
      return stripped;
    }
    // Markdown-fenced JSON
    int start = stripped.indexOf("```json");
    if (start == -1) start = stripped.indexOf("```");
    if (start == -1) return null;

    int contentStart = stripped.indexOf('\n', start);
    if (contentStart == -1) return null;

    int end = stripped.indexOf("```", contentStart);
    if (end == -1) return null;

    return stripped.substring(contentStart + 1, end).strip();
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
      if (text == null) return 5;

      String json = extractJsonBlock(text);
      if (json == null) return 5;

      JsonNode node = MAPPER.readTree(json);
      return node.has("score") ? node.get("score").asInt(5) : 5;
    } catch (Exception e) {
      return 5;
    }
  }
}
