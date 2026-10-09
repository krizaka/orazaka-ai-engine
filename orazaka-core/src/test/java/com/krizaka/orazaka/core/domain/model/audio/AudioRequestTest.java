package com.krizaka.orazaka.core.domain.model.audio;

import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.orazaka.core.domain.model.Context;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AudioRequestTest {

  private final Context ctx = new Context("user", "conv", Map.of(), Set.of());

  @Test
  void validConstruction() {
    var request = new AudioRequest("Hello world", "shimmer", "tts-1-hd", Map.of("speed", 1.0), ctx);
    assertEquals("Hello world", request.prompt());
    assertEquals("shimmer", request.voice());
    assertEquals("tts-1-hd", request.model());
  }

  @Test
  void nullVoice_defaultsToAlloy() {
    var request = new AudioRequest("prompt", null, null, null, ctx);
    assertEquals("alloy", request.voice());
  }

  @Test
  void blankVoice_defaultsToAlloy() {
    var request = new AudioRequest("prompt", "  ", null, null, ctx);
    assertEquals("alloy", request.voice());
  }

  @Test
  void nullModel_defaultsToTts1() {
    var request = new AudioRequest("prompt", null, null, null, ctx);
    assertEquals("tts-1", request.model());
  }
}
