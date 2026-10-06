package com.orazaka.core.infrastructure.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orazaka.core.domain.model.Context;
import com.orazaka.core.domain.model.image.ImageRequest;
import com.orazaka.core.domain.model.image.ImageUsage;
import com.orazaka.core.infrastructure.config.CoreProperties;
import com.orazaka.persistence.domain.ports.inbound.CatalogModelManager;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * [BILL-002] — the IMAGE_STEP quantity follows the work, not the request.
 *
 * <p>A divergence contract, through the real client and its real provider SDK, against a stub of
 * the OpenAI-compatible images endpoint: the request asks for one size and the provider returns
 * another. The bill must follow the image that came back. A test that built an {@code ImageUsage}
 * by hand would pass whichever the adapter used — which is the whole lesson of ADR-062.
 *
 * <p>{@code steps} is the one input no image carries: {@code sd-server} takes it at launch. It is
 * read from configuration, and {@code ImageStepsBindingTest} pins that the configuration is the one
 * the launcher reads. That remains the weakest link, and ADR-062 §5 says so.
 */
class ImageGeneratorClientImplTest {

  private HttpServer provider;

  @AfterEach
  void stop() {
    if (provider != null) {
      provider.stop(0);
    }
  }

  private String serve(String body) throws IOException {
    provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    provider.createContext(
        "/",
        exchange -> {
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    provider.start();
    return "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1";
  }

  private static String png(int width, int height) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
    return Base64.getEncoder().encodeToString(bytes.toByteArray());
  }

  private static ImageGeneratorClientImpl client(String baseUrl, int steps) {
    CoreProperties properties =
        new CoreProperties(
            "ollama",
            null,
            null,
            null,
            null,
            new CoreProperties.ImageConfig(
                new CoreProperties.ImageGenerationConfig(
                    "localai-image",
                    baseUrl,
                    "not-required",
                    "stable-diffusion",
                    512,
                    512,
                    1,
                    steps,
                    5000,
                    5000)),
            null,
            null);
    CatalogModelManager catalog = mock(CatalogModelManager.class);
    when(catalog.getDefaultModelByCategory(anyString())).thenReturn(Optional.empty());
    when(catalog.getModelsByCategory(anyString())).thenReturn(List.of());
    when(catalog.getProviderBaseUrl(anyString())).thenReturn(null);
    return new ImageGeneratorClientImpl(properties, catalog);
  }

  private static ImageRequest askFor(int width, int height) {
    return new ImageRequest(
        "a lighthouse at dusk", width, height, "stable-diffusion", Map.of(), Context.anonymous());
  }

  @Test
  @DisplayName(
      "[BILL-002] asked for 512×512, returned 768×512: the bill measures the image that came back")
  void theBilledDimensionsAreTheReturnedImagesNotTheRequests() throws IOException {
    String baseUrl =
        serve("{\"created\":1700000000,\"data\":[{\"b64_json\":\"" + png(768, 512) + "\"}]}");

    ImageUsage usage = client(baseUrl, 20).generateImage(askFor(512, 512)).usage();

    assertThat(usage.reported()).isTrue();
    assertThat(usage.width())
        .as("width billed from something other than the image that came back")
        .isEqualTo(768);
    assertThat(usage.height()).isEqualTo(512);
    assertThat(usage.images()).isEqualTo(1);
    assertThat(usage.steps()).isEqualTo(20);
  }

  @Test
  @DisplayName(
      "[BILL-002] a provider that returns no decodable image is unmeasured — released, never billed at the request's size")
  void noDecodableImageIsUnmeasured() throws IOException {
    String baseUrl =
        serve("{\"created\":1700000000,\"data\":[{\"url\":\"http://127.0.0.1:1/nowhere.png\"}]}");

    ImageUsage usage = client(baseUrl, 20).generateImage(askFor(512, 512)).usage();

    assertThat(usage.reported()).isFalse();
  }
}
