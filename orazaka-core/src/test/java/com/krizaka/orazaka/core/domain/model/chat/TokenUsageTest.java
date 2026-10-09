package com.krizaka.orazaka.core.domain.model.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenUsageTest {

  @Test
  @DisplayName("a provider that reported nothing is unmeasured, not zero-consumption")
  void noneIsUnreported() {
    TokenUsage usage = TokenUsage.none();

    assertFalse(usage.reported());
    assertEquals(0, usage.kilotokens().compareTo(BigDecimal.ZERO));
  }

  @Test
  @DisplayName("a genuine zero is measured — the distinction decides release versus floor billing")
  void aReportedZeroIsStillReported() {
    TokenUsage usage = TokenUsage.reported(0, 0, 0);

    assertTrue(usage.reported());
    assertEquals(0, usage.kilotokens().compareTo(BigDecimal.ZERO));
  }

  @Test
  @DisplayName("kilotokens is what chat is priced in")
  void kilotokens() {
    assertEquals(
        0, TokenUsage.reported(1200, 2200, 3400).kilotokens().compareTo(new BigDecimal("3.4")));
  }

  @Test
  @DisplayName("a missing total is summed from the halves")
  void sumsWhenTheProviderOmitsTheTotal() {
    TokenUsage usage = TokenUsage.reported(120, 80, null);

    assertEquals(200, usage.totalTokens());
  }

  @Test
  @DisplayName("a provider's own total wins — some count tokens in neither half")
  void keepsTheProvidersTotalWhenItIsNotTheSum() {
    // Cached and reasoning tokens show up in the total without appearing in prompt or completion;
    // re-deriving the sum here would silently under-bill them.
    TokenUsage usage = TokenUsage.reported(120, 80, 500);

    assertEquals(500, usage.totalTokens());
  }

  @Test
  @DisplayName("a half the provider omitted counts as zero, not as unreported")
  void defaultsMissingHalvesToZero() {
    TokenUsage usage = TokenUsage.reported(null, 80, 80);

    assertTrue(usage.reported());
    assertEquals(0, usage.promptTokens());
  }

  @Test
  void rejectsNegativeCounts() {
    assertThrows(IllegalArgumentException.class, () -> new TokenUsage(-2, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new TokenUsage(0, -5, 0));
    assertThrows(IllegalArgumentException.class, () -> new TokenUsage(0, 0, -3));
  }

  @Test
  @DisplayName("a response with no usage still answers, so no call site has to null-check")
  void responseDefaultsToNone() {
    InternalChatResponse response = new InternalChatResponse("hi", "conv-1", null, null);

    assertFalse(response.tokenUsage().reported());
    assertTrue(response.metadata().isEmpty());
  }

  @Test
  @DisplayName("the three-argument overload keeps every non-inference call site working")
  void legacyOverloadYieldsNone() {
    InternalChatResponse response =
        new InternalChatResponse("chunk", "conv-1", java.util.Map.of("provider", "ollama"));

    assertFalse(response.tokenUsage().reported());
    assertEquals("ollama", response.metadata().get("provider"));
  }
}
