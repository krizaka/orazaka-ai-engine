package com.orazaka.core.infrastructure.adapter.ai;

import com.orazaka.core.application.engine.Engine;
import com.orazaka.core.domain.model.audio.AudioRequest;
import com.orazaka.core.domain.model.audio.AudioResponse;
import com.orazaka.core.domain.ports.outbound.AudioGeneratorClient;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Outbound adapter implementing {@link AudioGeneratorClient} by delegating speech generation to the
 * core Engine.
 */
@Component
class AudioGeneratorClientImpl implements AudioGeneratorClient {

  private final Engine engine;

  public AudioGeneratorClientImpl(Engine engine) {
    this.engine = Objects.requireNonNull(engine, "Engine must not be null");
  }

  @Override
  public AudioResponse generateAudio(AudioRequest request) {
    byte[] data = engine.generateSpeech(request);
    return new AudioResponse(data, "mp3");
  }
}
