package com.krizaka.orazaka.core.application.routing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

class ProviderClassifierTest {

  @Test
  void ollama_returnsOllama() {
    assertEquals("ollama", ProviderClassifier.ollama());
  }

  @Test
  void openai_returnsOpenai() {
    assertEquals("openai", ProviderClassifier.openai());
  }

  @Test
  void anthropic_returnsAnthropic() {
    assertEquals("anthropic", ProviderClassifier.anthropic());
  }

  @Test
  void gemini_returnsGemini() {
    assertEquals("gemini", ProviderClassifier.gemini());
  }

  @ParameterizedTest
  @ValueSource(strings = {"openai", "claude", "anthropic", "gemini", "google"})
  void isCommercial_commercialProviders_returnsTrue(String provider) {
    assertTrue(ProviderClassifier.isCommercial(provider));
  }

  @ParameterizedTest
  @ValueSource(strings = {"OPENAI", "Claude", "ANTHROPIC", "Gemini", "GOOGLE"})
  void isCommercial_caseInsensitive(String provider) {
    assertTrue(ProviderClassifier.isCommercial(provider));
  }

  @Test
  void isCommercial_localProvider_returnsFalse() {
    assertFalse(ProviderClassifier.isCommercial("ollama"));
  }

  @Test
  void isCommercial_null_returnsFalse() {
    assertFalse(ProviderClassifier.isCommercial(null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"ollama", "localai"})
  void isLocal_localProviders_returnsTrue(String provider) {
    assertTrue(ProviderClassifier.isLocal(provider));
  }

  @Test
  void isLocal_commercialProvider_returnsFalse() {
    assertFalse(ProviderClassifier.isLocal("openai"));
  }

  @Test
  void isLocal_null_returnsFalse() {
    assertFalse(ProviderClassifier.isLocal(null));
  }

  @Test
  void resolveFromOptions_ollamaOptions_returnsOllama() {
    assertEquals(
        "ollama", ProviderClassifier.resolveFromOptions(OllamaChatOptions.builder().build()));
  }

  @Test
  void resolveFromOptions_openaiOptions_returnsOpenai() {
    assertEquals(
        "openai", ProviderClassifier.resolveFromOptions(OpenAiChatOptions.builder().build()));
  }

  @Test
  void resolveFromOptions_anthropicOptions_returnsAnthropic() {
    assertEquals(
        "anthropic", ProviderClassifier.resolveFromOptions(AnthropicChatOptions.builder().build()));
  }

  @Test
  void resolveFromOptions_geminiOptions_returnsGemini() {
    assertEquals(
        "gemini", ProviderClassifier.resolveFromOptions(GoogleGenAiChatOptions.builder().build()));
  }

  @Test
  void resolveFromOptions_null_returnsNull() {
    assertNull(ProviderClassifier.resolveFromOptions(null));
  }

  @Test
  void resolveModelName_openaiOptions_returnsModelName() {
    var options = OpenAiChatOptions.builder().model("gpt-4").build();
    assertEquals("gpt-4", ProviderClassifier.resolveModelName(options));
  }

  @Test
  void resolveModelName_anthropicOptions_returnsModelName() {
    var options = AnthropicChatOptions.builder().model("claude-3").build();
    assertEquals("claude-3", ProviderClassifier.resolveModelName(options));
  }

  @Test
  void resolveModelName_geminiOptions_returnsModelName() {
    var options = GoogleGenAiChatOptions.builder().model("gemini-pro").build();
    assertEquals("gemini-pro", ProviderClassifier.resolveModelName(options));
  }

  @Test
  void resolveModelName_ollamaOptions_returnsModelName() {
    var options = OllamaChatOptions.builder().model("llama3").build();
    assertEquals("llama3", ProviderClassifier.resolveModelName(options));
  }

  @Test
  void resolveModelName_null_returnsNull() {
    assertNull(ProviderClassifier.resolveModelName(null));
  }
}
