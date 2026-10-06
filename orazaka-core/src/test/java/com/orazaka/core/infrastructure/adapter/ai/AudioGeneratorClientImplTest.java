package com.orazaka.core.infrastructure.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orazaka.core.application.engine.Engine;
import com.orazaka.core.domain.model.audio.AudioRequest;
import com.orazaka.core.domain.model.audio.AudioResponse;
import org.junit.jupiter.api.Test;

class AudioGeneratorClientImplTest {

  @Test
  void testConstructorNullCheck() {
    assertThatThrownBy(() -> new AudioGeneratorClientImpl(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Engine must not be null");
  }

  @Test
  void testGenerateAudio() {
    Engine engine = mock(Engine.class);
    AudioGeneratorClientImpl client = new AudioGeneratorClientImpl(engine);

    AudioRequest request =
        new AudioRequest(
            "hello world",
            "alloy",
            "tts-1",
            java.util.Map.of(),
            com.orazaka.core.domain.model.Context.anonymous());
    byte[] mockAudio = new byte[] {1, 2, 3};
    when(engine.generateSpeech(request)).thenReturn(mockAudio);

    AudioResponse response = client.generateAudio(request);
    assertThat(response).isNotNull();
    assertThat(response.audioData()).isEqualTo(mockAudio);
    assertThat(response.format()).isEqualTo("mp3");
  }
}
