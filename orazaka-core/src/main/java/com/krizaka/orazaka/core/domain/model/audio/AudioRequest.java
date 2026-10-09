package com.krizaka.orazaka.core.domain.model.audio;

import com.krizaka.orazaka.core.domain.model.AiRequest;
import com.krizaka.orazaka.core.domain.model.Context;
import java.util.Map;

/**
 * Standardized audio request record for Text-To-Speech generation.
 *
 * @param prompt The text content to convert to speech.
 * @param voice The voice model name (defaults to "alloy").
 * @param model The target model name (defaults to "tts-1").
 * @param settings Additional provider-specific settings.
 * @param context The execution context carrying user preferences.
 */
public record AudioRequest(
    String prompt, String voice, String model, Map<String, Object> settings, Context context)
    implements AiRequest {

  public AudioRequest {
    AiRequest.requireValid(prompt, context);
    voice = (voice != null && !voice.isBlank()) ? voice : "alloy";
    model = (model != null && !model.isBlank()) ? model : "tts-1";
    settings = (settings != null) ? Map.copyOf(settings) : Map.of();
  }
}
