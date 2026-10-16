package com.krizaka.orazaka.core.domain.model.image;

import com.krizaka.orazaka.core.domain.model.AiRequest;
import com.krizaka.orazaka.core.domain.model.Context;
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
 * @param direction Negative prompt, seed and step count (optional; never null once built).
 */
public record ImageRequest(
    String prompt,
    Integer width,
    Integer height,
    String model,
    Map<String, Object> settings,
    Context context,
    ImageDirection direction)
    implements AiRequest {
  public ImageRequest {
    AiRequest.requireValid(prompt, context);
    settings = settings != null ? Map.copyOf(settings) : Map.of();
    direction = direction != null ? direction : ImageDirection.none();
  }

  /**
   * A request that leaves seed, negative prompt and steps to the model's recipe.
   *
   * @param prompt descriptive text for the image
   * @param width desired width in pixels (optional)
   * @param height desired height in pixels (optional)
   * @param model desired model name (optional)
   * @param settings provider-specific settings
   * @param context the execution context
   */
  public ImageRequest(
      String prompt,
      Integer width,
      Integer height,
      String model,
      Map<String, Object> settings,
      Context context) {
    this(prompt, width, height, model, settings, context, ImageDirection.none());
  }
}
