package com.orazaka.core.infrastructure.adapter.ai;

import com.orazaka.core.domain.model.MediaCategory;
import com.orazaka.core.domain.model.image.ImageRequest;
import com.orazaka.core.domain.model.image.ImageResponse;
import com.orazaka.core.domain.model.image.ImageUsage;
import com.orazaka.core.domain.ports.outbound.ImageGeneratorClient;
import com.orazaka.core.infrastructure.config.CoreProperties;
import com.orazaka.core.infrastructure.config.SafeImageModel;
import com.orazaka.core.infrastructure.support.CoreException;
import com.orazaka.persistence.domain.model.CatalogModelDto;
import com.orazaka.persistence.domain.ports.inbound.CatalogModelManager;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.stereotype.Component;

/**
 * Adapter implementation of {@link ImageGeneratorClient} wrapping Spring AI's {@link ImageModel}.
 */
@Component
class ImageGeneratorClientImpl implements ImageGeneratorClient {

  private static final Logger logger = LoggerFactory.getLogger(ImageGeneratorClientImpl.class);
  private static final int DEFAULT_TIMEOUT_MS = 180_000;

  /** The adapter always asks for one image ({@code n(1)} below); the meter reports the same. */
  private static final int SINGLE_IMAGE = 1;

  /** stable-diffusion.cpp's own default, used when the deployment did not state one. */
  private static final int DEFAULT_STEPS = 20;

  private final CoreProperties properties;
  private final CatalogModelManager catalogModelManager;

  /**
   * Constructs the adapter with the CatalogModelManager and CoreProperties.
   *
   * @param properties Core configuration properties.
   * @param catalogModelManager The catalog model manager.
   */
  public ImageGeneratorClientImpl(
      CoreProperties properties, CatalogModelManager catalogModelManager) {
    this.properties = Objects.requireNonNull(properties, "CoreProperties must not be null");
    this.catalogModelManager =
        Objects.requireNonNull(catalogModelManager, "CatalogModelManager must not be null");
  }

  @Override
  public ImageResponse generateImage(ImageRequest request) {
    int height = request.height() != null ? request.height() : 512;
    int width = request.width() != null ? request.width() : 512;

    String activeModel = resolveModel(request.model());
    String providerName = resolveProviderName(activeModel);
    String baseUrl = resolveBaseUrl(providerName);
    String apiKey = resolveApiKey();

    logger.info(
        "ImageGeneratorClientImpl: Dynamically resolving provider baseUrl: {} for model: {}",
        baseUrl,
        activeModel);

    ImageModel dynamicImageModel = buildImageModel(baseUrl, apiKey, activeModel, height, width);

    OpenAiImageOptions executionOptions =
        OpenAiImageOptions.builder().model(activeModel).n(1).height(height).width(width).build();

    var response = dynamicImageModel.call(new ImagePrompt(request.prompt(), executionOptions));
    var generation = response.getResult();
    byte[] imageData = extractImageData(generation);
    String url = extractUrl(generation);

    if (imageData.length > 0 && url == null) {
      url = buildImageDataUrl(imageData);
    }
    return new ImageResponse(imageData, url, "png", measuredUsage(imageData));
  }

  /**
   * What IMAGE_STEP ({@code images × steps × megapixels}) is billed on, measured on the image the
   * provider returned [BILL-002].
   *
   * <p>It used to report the width and height this adapter <i>asked</i> for — a quantity taken from
   * the caller's own request, the same class of error as a scope a caller declares about itself. A
   * provider that returned a larger image billed the smaller one, and one that returned no image
   * billed a full one (ADR-062 §5). Now an image that cannot be decoded is unmeasured, and an
   * unmeasured generation releases its hold rather than being billed a guess.
   *
   * <p>{@code steps} is the input no image carries: sd-server takes it at launch, so it is still
   * read from configuration — the one quantity here that is a declaration rather than a
   * measurement.
   */
  private ImageUsage measuredUsage(byte[] imageData) {
    if (imageData.length == 0) {
      return ImageUsage.none();
    }
    try {
      java.awt.image.BufferedImage decoded =
          javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageData));
      if (decoded == null) {
        return ImageUsage.none();
      }
      return new ImageUsage(SINGLE_IMAGE, resolveSteps(), decoded.getWidth(), decoded.getHeight());
    } catch (java.io.IOException unreadable) {
      logger.warn("Generated image could not be decoded; the generation is unmeasured", unreadable);
      return ImageUsage.none();
    }
  }

  /**
   * The denoising-step count this deployment runs at.
   *
   * <p>Not a request parameter: {@code sd-server} takes {@code --steps} at launch, so the honest
   * source is the configuration both the launcher and this meter read. Falling back to
   * stable-diffusion.cpp's own default keeps an unconfigured deployment billed at what it actually
   * runs, rather than unbilled.
   */
  private int resolveSteps() {
    if (properties.image() != null
        && properties.image().generation() != null
        && properties.image().generation().steps() != null
        && properties.image().generation().steps() > 0) {
      return properties.image().generation().steps();
    }
    return DEFAULT_STEPS;
  }

  private String resolveModel(String requestModel) {
    if (requestModel != null && !requestModel.isBlank()) {
      return requestModel;
    }
    return catalogModelManager
        .getDefaultModelByCategory(MediaCategory.IMAGE.value())
        .map(CatalogModelDto::modelName)
        .orElse("stable-diffusion");
  }

  private String resolveProviderName(String activeModel) {
    List<CatalogModelDto> models =
        catalogModelManager.getModelsByCategory(MediaCategory.IMAGE.value());
    for (CatalogModelDto m : models) {
      if (m.modelName().equalsIgnoreCase(activeModel)) {
        return m.providerName();
      }
    }
    return "localai-image";
  }

  private String resolveBaseUrl(String providerName) {
    String baseUrl = catalogModelManager.getProviderBaseUrl(providerName);
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = resolveBaseUrlFromProperties();
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new CoreException(
          "Missing required configuration: image generation baseUrl. "
              + "Configure via orazaka.core.image.generation.base-url or register a provider in the catalog.");
    }
    return baseUrl;
  }

  private String resolveBaseUrlFromProperties() {
    if (properties.image() != null
        && properties.image().generation() != null
        && properties.image().generation().baseUrl() != null) {
      return properties.image().generation().baseUrl();
    }
    return null;
  }

  private String resolveApiKey() {
    if (properties.image() != null
        && properties.image().generation() != null
        && properties.image().generation().apiKey() != null) {
      return properties.image().generation().apiKey();
    }
    return "not-required";
  }

  private ImageModel buildImageModel(
      String baseUrl, String apiKey, String activeModel, int height, int width) {
    // Spring AI 2.0 derives the com.openai client from the options via OpenAiSetup, so the provider
    // endpoint (base-url + api-key) and the long read timeout — critical for local diffusion
    // backends
    // — are set on the OpenAiImageOptions.
    OpenAiImageOptions executionOptions =
        OpenAiImageOptions.builder()
            .model(activeModel)
            .n(1)
            .height(height)
            .width(width)
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .timeout(Duration.ofMillis(resolveTimeout(false)))
            .build();

    return new SafeImageModel(OpenAiImageModel.builder().options(executionOptions).build());
  }

  private int resolveTimeout(boolean isConnect) {
    if (properties.image() != null && properties.image().generation() != null) {
      var gen = properties.image().generation();
      Integer timeout = isConnect ? gen.connectTimeoutMs() : gen.readTimeoutMs();
      if (timeout != null) {
        return timeout;
      }
    }
    return DEFAULT_TIMEOUT_MS;
  }

  private byte[] extractImageData(ImageGeneration generation) {
    if (generation == null || generation.getOutput() == null) {
      return new byte[0];
    }
    byte[] data = decodeBase64Image(generation.getOutput().getB64Json());
    if (data.length == 0) {
      data = extractImageViaReflection(generation.getOutput());
    }
    return data;
  }

  private String extractUrl(ImageGeneration generation) {
    if (generation == null || generation.getOutput() == null) {
      return null;
    }
    return generation.getOutput().getUrl();
  }

  private byte[] decodeBase64Image(String b64) {
    if (b64 == null || b64.isBlank()) {
      return new byte[0];
    }
    try {
      return Base64.getDecoder().decode(b64.trim());
    } catch (IllegalArgumentException e) {
      logger.warn("Failed to decode base64 image data", e);
      return new byte[0];
    }
  }

  private byte[] extractImageViaReflection(Object output) {
    try {
      var method = output.getClass().getMethod("getImage");
      Object imgObj = method.invoke(output);
      if (imgObj instanceof byte[] bytes) {
        return bytes;
      }
    } catch (ReflectiveOperationException e) {
      logger.trace("Failed reflective fallback extraction via getImage()", e);
    }
    return new byte[0];
  }

  private String buildImageDataUrl(byte[] imageData) {
    return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageData);
  }
}
