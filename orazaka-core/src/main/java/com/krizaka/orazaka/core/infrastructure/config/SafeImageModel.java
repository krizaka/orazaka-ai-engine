package com.krizaka.orazaka.core.infrastructure.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

/**
 * Decorator for {@link ImageModel} that catches communication exceptions and returns a fallback 1x1
 * transparent PNG data URL to ensure the client playground remains stable.
 */
public class SafeImageModel implements ImageModel {

  private static final Logger logger = LoggerFactory.getLogger(SafeImageModel.class);

  private final ImageModel delegate;

  public SafeImageModel(ImageModel delegate) {
    this.delegate = delegate;
  }

  @Override
  public ImageResponse call(ImagePrompt prompt) {
    try {
      return delegate.call(prompt);
    } catch (RuntimeException e) {
      logger.error(
          "SafeImageModel: Image generation call failed, returning transparent fallback", e);
      // Fallback 1x1 transparent PNG representation
      Image image =
          new Image(
              null,
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNgYGBgAAAABQABpfZFQAAAAABJRU5ErkJggg==");
      return new ImageResponse(List.of(new ImageGeneration(image)));
    }
  }
}
