package com.krizaka.orazaka.interceptor.validation;

import com.krizaka.orazaka.core.domain.model.AssertionContract;
import com.krizaka.orazaka.core.domain.model.chat.EngineMessage;
import com.krizaka.orazaka.core.domain.ports.outbound.ProviderChatClient;
import com.krizaka.orazaka.core.domain.ports.outbound.TestShaperPort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Concrete adapter implementing the {@link TestShaperPort} outbound port for Tier D (Test-Driven
 * Response) assertion generation.
 *
 * <p>Uses a constructor-injected {@link ProviderChatClient} (preferably a fast reasoning model like
 * Qwen-2.5-Coder) to generate structured JSON assertion contracts. The prompt instructs the model
 * to produce machine-evaluable assertion rules that the LLM response must satisfy.
 *
 * <p>Graceful degradation: returns {@link AssertionContract#empty()} on any failure.
 *
 * @since 1.1.0
 */
public class ModelTestShaperAdapter implements TestShaperPort {

  private static final Logger logger = LoggerFactory.getLogger(ModelTestShaperAdapter.class);
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

  private final ProviderChatClient chatModel;

  /**
   * Constructs a ModelTestShaperAdapter.
   *
   * @param chatModel The model port to use for assertion generation (e.g., Qwen-2.5-Coder).
   */
  public ModelTestShaperAdapter(ProviderChatClient chatModel) {
    this.chatModel =
        Objects.requireNonNull(chatModel, "ProviderChatClient must not be null for TDR Tier D");
  }

  @Override
  public AssertionContract generateAssertions(String userPrompt, String responseText) {
    try {
      String userContent = "User Prompt:\n" + userPrompt + "\n\nAI Response:\n" + responseText;

      String text =
          chatModel.completeWithDefault(
              List.of(EngineMessage.system(TDR_SYSTEM_PROMPT), EngineMessage.user(userContent)),
              0.0);

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

  private String extractJsonBlock(String text) {
    return JsonBlocks.extract(text, false);
  }
}
