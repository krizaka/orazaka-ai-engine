package com.krizaka.orazaka.core.infrastructure.adapter.ai;

import com.krizaka.orazaka.core.application.engine.Engine;
import com.krizaka.orazaka.core.domain.model.audio.AudioRequest;
import com.krizaka.orazaka.core.domain.model.audio.AudioResponse;
import com.krizaka.orazaka.core.domain.ports.outbound.AudioGeneratorClient;
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
