package com.krizaka.orazaka.interceptor.validation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.orazaka.core.domain.model.AssertionContract;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** Unit tests for {@link SpringAiTestShaperAdapter}. */
@ExtendWith(MockitoExtension.class)
class SpringAiTestShaperAdapterTest {

  @Mock private ChatModel chatModel;

  @Test
  @DisplayName("Generates valid assertion contract from well-formed JSON response")
  void generateAssertions_validJson() {
    String modelResponse =
        """
        {
          "schemaName": "code-review",
          "assertions": [
            "Response must contain a code block",
            "Response must not contain placeholder values like TODO"
          ]
        }
        """;
    when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse(modelResponse));

    var adapter = new SpringAiTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("Write a function", "def foo(): pass");

    assertThat(contract.schemaName()).isEqualTo("code-review");
    assertThat(contract.assertions()).hasSize(2);
    assertThat(contract.assertions().get(0)).contains("code block");
    assertThat(contract.hasAssertions()).isTrue();
    assertThat(contract.generatedAt()).isNotBlank();
  }

  @Test
  @DisplayName("Generates assertions from markdown-fenced JSON response")
  void generateAssertions_markdownFencedJson() {
    String modelResponse =
        """
        Here are the assertions:
        ```json
        {
          "schemaName": "fenced",
          "assertions": ["check one"]
        }
        ```
        """;
    when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse(modelResponse));

    var adapter = new SpringAiTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("prompt", "response");

    assertThat(contract.schemaName()).isEqualTo("fenced");
    assertThat(contract.assertions()).containsExactly("check one");
  }

  @Test
  @DisplayName("Returns empty contract when model returns null response")
  void generateAssertions_nullResponse() {
    when(chatModel.call(any(Prompt.class))).thenReturn(null);

    var adapter = new SpringAiTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("prompt", "response");

    assertThat(contract).isEqualTo(AssertionContract.empty());
  }

  @Test
  @DisplayName("Returns empty contract when model returns empty text")
  void generateAssertions_emptyText() {
    when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse(""));

    var adapter = new SpringAiTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("prompt", "response");

    assertThat(contract).isEqualTo(AssertionContract.empty());
  }

  @Test
  @DisplayName("Returns empty contract when model returns invalid JSON")
  void generateAssertions_invalidJson() {
    when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse("{invalid json"));

    var adapter = new SpringAiTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("prompt", "response");

    assertThat(contract.hasAssertions()).isFalse();
  }

  @Test
  @DisplayName("Returns empty contract when model returns plain text (no JSON)")
  void generateAssertions_plainText() {
    when(chatModel.call(any(Prompt.class)))
        .thenReturn(mockResponse("I cannot generate assertions for this request."));

    var adapter = new SpringAiTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("prompt", "response");

    assertThat(contract.hasAssertions()).isFalse();
  }

  @Test
  @DisplayName("Gracefully degrades when model call throws RuntimeException")
  void generateAssertions_runtimeException() {
    when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Model unavailable"));

    var adapter = new SpringAiTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("prompt", "response");

    assertThat(contract).isEqualTo(AssertionContract.empty());
  }

  @Test
  @DisplayName("Handles JSON with missing assertions field")
  void generateAssertions_missingAssertionsField() {
    String modelResponse = "{\"schemaName\": \"partial\"}";
    when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse(modelResponse));

    var adapter = new SpringAiTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("prompt", "response");

    assertThat(contract.schemaName()).isEqualTo("partial");
    assertThat(contract.assertions()).isEmpty();
  }

  @Test
  @DisplayName("Handles JSON with missing schemaName field")
  void generateAssertions_missingSchemaNameField() {
    String modelResponse = "{\"assertions\": [\"check\"]}";
    when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse(modelResponse));

    var adapter = new SpringAiTestShaperAdapter(chatModel);
    AssertionContract contract = adapter.generateAssertions("prompt", "response");

    assertThat(contract.schemaName()).isEqualTo("tdr");
    assertThat(contract.assertions()).containsExactly("check");
  }

  @Test
  @DisplayName("Constructor rejects null ChatModel")
  void constructor_nullChatModel_throws() {
    assertThatThrownBy(() -> new SpringAiTestShaperAdapter(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("ChatModel");
  }

  private ChatResponse mockResponse(String text) {
    if (text == null) return null;
    var output = new AssistantMessage(text);
    var generation = new Generation(output);
    return new ChatResponse(List.of(generation));
  }
}
