package com.krizaka.orazaka.interceptor.validation;

import com.krizaka.orazaka.core.domain.model.AssertionContract;
import com.krizaka.orazaka.core.domain.ports.outbound.TestShaperPort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Concrete adapter implementing the {@link TestShaperPort} outbound port for Tier D (Test-Driven
 * Response) assertion generation.
 *
 * <p>Uses a constructor-injected Spring AI {@link ChatModel} (preferably a fast reasoning model
 * like Qwen-2.5-Coder) to generate structured JSON assertion contracts. The prompt instructs the
 * model to produce machine-evaluable assertion rules that the LLM response must satisfy.
 *
 * <p>Graceful degradation: returns {@link AssertionContract#empty()} on any failure.
 *
 * @since 1.1.0
 */
public class SpringAiTestShaperAdapter implements TestShaperPort {

  private static final Logger logger = LoggerFactory.getLogger(SpringAiTestShaperAdapter.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String TDR_SYSTEM_PROMPT =
      """
      You are a test assertion generator. Given a user prompt and an AI response, generate a set of
      machine-evaluable assertion rules that the response MUST satisfy.

      Respond with ONLY a valid JSON object in this exact format:
      {
        "schemaName": "<brief context identifier>",
        "assertions": [
          "Response must contain a code block",
          "Response must address the user's specific question about X",
          "Response must not contain placeholder values like TODO or FIXME"
        ]
      }

      Focus on:
      1. Structural completeness (does the response have all requested parts?)
      2. Factual accuracy markers (does it reference correct concepts?)
      3. Anti-hallucination checks (does it avoid unsupported claims?)
      4. Format compliance (does it match the expected output format?)
      """;

  private final ChatModel chatModel;

  /**
   * Constructs a SpringAiTestShaperAdapter.
   *
   * @param chatModel The ChatModel to use for assertion generation (e.g., Qwen-2.5-Coder).
   */
  public SpringAiTestShaperAdapter(ChatModel chatModel) {
    this.chatModel = Objects.requireNonNull(chatModel, "ChatModel must not be null for TDR Tier D");
  }

  @Override
  public AssertionContract generateAssertions(String userPrompt, String responseText) {
    try {
      String userContent = "User Prompt:\n" + userPrompt + "\n\nAI Response:\n" + responseText;

      ChatOptions options = ChatOptions.builder().temperature(0.0).build();
      ChatResponse response =
          chatModel.call(
              new Prompt(
                  List.of(new SystemMessage(TDR_SYSTEM_PROMPT), new UserMessage(userContent)),
                  options));

      String text =
          java.util.Optional.ofNullable(response)
              .map(ChatResponse::getResult)
              .map(Generation::getOutput)
              .map(org.springframework.ai.chat.messages.AssistantMessage::getText)
              .orElse(null);

      if (text == null || text.isBlank()) {
        logger.warn("Tier D — TDR model returned empty response. Returning empty contract.");
        return AssertionContract.empty();
      }

      return parseContract(text);
    } catch (RuntimeException e) {
      logger.error("Tier D — TDR assertion generation failed. Gracefully degrading.", e);
      return AssertionContract.empty();
    }
  }

  /**
   * Parses the model's JSON response into an AssertionContract.
   *
   * @param text The raw model output.
   * @return Parsed contract, or empty on parse failure.
   */
  private AssertionContract parseContract(String text) {
    try {
      String json = extractJsonBlock(text);
      if (json == null) {
        logger.warn("Tier D — Could not extract JSON from TDR response.");
        return AssertionContract.empty();
      }

      var node = MAPPER.readTree(json);
      String schemaName = node.has("schemaName") ? node.get("schemaName").asString("tdr") : "tdr";
      List<String> assertions =
          node.has("assertions")
              ? MAPPER.convertValue(node.get("assertions"), new TypeReference<List<String>>() {})
              : List.of();

      return new AssertionContract(schemaName, assertions, Instant.now().toString());
    } catch (Exception e) {
      logger.warn("Tier D — Failed to parse TDR assertion JSON.", e);
      return AssertionContract.empty();
    }
  }

  /**
   * Extracts a JSON block from potentially markdown-fenced text.
   *
   * @param text The response text.
   * @return The extracted JSON string, or null if none found.
   */
  private String extractJsonBlock(String text) {
    if (text == null) return null;
    String stripped = text.strip();
    if (stripped.startsWith("{")) return stripped;

    int start = stripped.indexOf("```json");
    if (start == -1) start = stripped.indexOf("```");
    if (start == -1) return null;

    int contentStart = stripped.indexOf('\n', start);
    if (contentStart == -1) return null;

    int end = stripped.indexOf("```", contentStart);
    if (end == -1) return null;

    return stripped.substring(contentStart + 1, end).strip();
  }
}
