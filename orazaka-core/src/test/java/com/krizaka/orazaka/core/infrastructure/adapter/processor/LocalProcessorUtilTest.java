package com.krizaka.orazaka.core.infrastructure.adapter.processor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link LocalProcessorUtil}. */
class LocalProcessorUtilTest {

  // --- isAllZeros ---

  @Test
  void isAllZeros_allZeros_returnsTrue() {
    assertTrue(LocalProcessorUtil.isAllZeros(new byte[] {0, 0, 0, 0}));
  }

  @Test
  void isAllZeros_hasNonZero_returnsFalse() {
    assertFalse(LocalProcessorUtil.isAllZeros(new byte[] {0, 1, 0}));
  }

  @Test
  void isAllZeros_emptyArray_returnsTrue() {
    assertTrue(LocalProcessorUtil.isAllZeros(new byte[0]));
  }

  @Test
  void isAllZeros_singleZero_returnsTrue() {
    assertTrue(LocalProcessorUtil.isAllZeros(new byte[] {0}));
  }

  @Test
  void isAllZeros_singleNonZero_returnsFalse() {
    assertFalse(LocalProcessorUtil.isAllZeros(new byte[] {42}));
  }

  // --- resolveWhisperModel ---

  @Test
  void resolveWhisperModel_explicitModel_usesIt() {
    assertEquals(
        "custom-model", LocalProcessorUtil.resolveWhisperModel("custom-model", "whisper-1"));
  }

  @Test
  void resolveWhisperModel_nullModel_usesDefault() {
    assertEquals("whisper-1", LocalProcessorUtil.resolveWhisperModel(null, "whisper-1"));
  }

  @Test
  void resolveWhisperModel_blankModel_usesDefault() {
    assertEquals("whisper-1", LocalProcessorUtil.resolveWhisperModel("  ", "whisper-1"));
  }

  @Test
  void resolveWhisperModel_containsWhisper_normalizesToWhisper1() {
    assertEquals(
        "whisper-1", LocalProcessorUtil.resolveWhisperModel("Whisper-Large-v3", "fallback"));
  }

  @Test
  void resolveWhisperModel_defaultContainsWhisper_normalizesToWhisper1() {
    assertEquals("whisper-1", LocalProcessorUtil.resolveWhisperModel(null, "whisper-large"));
  }

  @Test
  void resolveWhisperModel_nonWhisperDefault_usesDefault() {
    assertEquals("custom-stt", LocalProcessorUtil.resolveWhisperModel(null, "custom-stt"));
  }
}
