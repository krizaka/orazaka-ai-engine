package com.krizaka.orazaka.core.infrastructure.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.orazaka.core.application.engine.Engine;
import com.krizaka.orazaka.core.domain.model.audio.AudioRequest;
import com.krizaka.orazaka.core.domain.model.audio.AudioResponse;
import org.junit.jupiter.api.Test;

class AudioGeneratorClientImplTest {

  @Test
  void constructorNullCheck() {
    assertThatThrownBy(() -> new AudioGeneratorClientImpl(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Engine must not be null");
  }

  @Test
  void generateAudio() {
    Engine engine = mock(Engine.class);
    AudioGeneratorClientImpl client = new AudioGeneratorClientImpl(engine);

    AudioRequest request =
        new AudioRequest(
            "hello world",
            "alloy",
            "tts-1",
            java.util.Map.of(),
            com.krizaka.orazaka.core.domain.model.Context.anonymous());
    byte[] mockAudio = new byte[] {1, 2, 3};
    when(engine.generateSpeech(request)).thenReturn(mockAudio);

    AudioResponse response = client.generateAudio(request);
    assertThat(response).isNotNull();
    assertThat(response.audioData()).isEqualTo(mockAudio);
    assertThat(response.format()).isEqualTo("mp3");
  }

  @Test
  void theDeclaredFormatIsTheFormatReturned() {
    Engine engine = mock(Engine.class);
    AudioRequest request =
        new AudioRequest(
            "bonjour",
            null,
            "piper-fr-siwis-medium",
            java.util.Map.of(AudioRequest.RESPONSE_FORMAT, "wav"),
            com.krizaka.orazaka.core.domain.model.Context.anonymous());
    when(engine.generateSpeech(request)).thenReturn(new byte[] {1});

    assertThat(new AudioGeneratorClientImpl(engine).generateAudio(request).format())
        .isEqualTo("wav");
  }
}
