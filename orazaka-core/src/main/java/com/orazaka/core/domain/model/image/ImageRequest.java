package com.orazaka.core.domain.model.image;

import com.orazaka.core.domain.model.AiRequest;
import com.orazaka.core.domain.model.Context;
import java.util.Map;

/**
 * Standardized image request record for multi-modal generation.
 *
 * @param prompt Descriptive text for the image to generate.
 * @param width Desired width in pixels (optional).
 * @param height Desired height in pixels (optional).
 * @param model Desired AI model name (optional).
 * @param settings Additional provider-specific settings.
 * @param context The execution context carrying user preferences.
 */
public record ImageRequest(
    String prompt,
    Integer width,
    Integer height,
    String model,
    Map<String, Object> settings,
    Context context)
    implements AiRequest {
  public ImageRequest {
    AiRequest.requireValid(prompt, context);
    settings = (settings != null) ? Map.copyOf(settings) : Map.of();
  }
}
