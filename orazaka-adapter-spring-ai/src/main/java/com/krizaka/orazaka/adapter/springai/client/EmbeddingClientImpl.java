package com.krizaka.orazaka.adapter.springai.client;

import com.krizaka.orazaka.adapter.springai.spend.SpendGuard;
import com.krizaka.orazaka.core.domain.model.MediaCategory;
import com.krizaka.orazaka.core.domain.model.ModelEndpoint;
import com.krizaka.orazaka.core.domain.model.embedding.EmbeddingException;
import com.krizaka.orazaka.core.domain.model.embedding.EmbeddingResult;
import com.krizaka.orazaka.core.domain.ports.outbound.EmbeddingClient;
import com.krizaka.orazaka.core.domain.ports.outbound.ModelEndpointResolver;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Adapter of {@link EmbeddingClient} for any OpenAI-compatible {@code /v1/embeddings} endpoint
 * (serverless inference, LocalAI, Ollama). The model and the endpoint are data: {@code
 * orazaka_models} (category {@code embedding}) and {@code ai_providers}, resolved through {@link
 * ModelEndpointResolver} (ADR-030) — nothing here names a provider.
 */
public class EmbeddingClientImpl implements EmbeddingClient {

  private static final Logger logger = LoggerFactory.getLogger(EmbeddingClientImpl.class);
  private static final int MAX_BATCH = 128;
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final ModelEndpointResolver endpoints;
  private final RestClient.Builder restClientBuilder;

  private final SpendGuard spendGuard;

  public EmbeddingClientImpl(ModelEndpointResolver endpoints, SpendGuard spendGuard) {
    this(
        endpoints,
        RestClient.builder()
            .requestFactory(
                new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build())),
        spendGuard);
  }

  EmbeddingClientImpl(ModelEndpointResolver endpoints, RestClient.Builder restClientBuilder) {
    this(endpoints, restClientBuilder, SpendGuard.unlimited());
  }

  EmbeddingClientImpl(
      ModelEndpointResolver endpoints,
      RestClient.Builder restClientBuilder,
      SpendGuard spendGuard) {
    this.spendGuard = Objects.requireNonNull(spendGuard, "SpendGuard cannot be null");
    this.endpoints = Objects.requireNonNull(endpoints, "ModelEndpointResolver cannot be null");
    this.restClientBuilder = Objects.requireNonNull(restClientBuilder, "builder cannot be null");
  }

  /** Wire shape of the OpenAI embeddings response (only what is read). */
  record Response(List<Item> data, Usage usage, String model) {
    record Item(int index, List<Double> embedding) {}

    record Usage(Integer prompt_tokens) {}
  }

  @Override
  public EmbeddingResult embed(List<String> inputs, String model) {
    if (inputs == null || inputs.isEmpty()) {
      throw new IllegalArgumentException("at least one input is required");
    }
    if (inputs.size() > MAX_BATCH) {
      throw new IllegalArgumentException("at most " + MAX_BATCH + " inputs per request");
    }
    if (inputs.stream().anyMatch(i -> i == null || i.isBlank())) {
      throw new IllegalArgumentException("inputs must not be blank");
    }
    ModelEndpoint endpoint =
        (model == null || model.isBlank()
                ? endpoints.resolveDefault(MediaCategory.EMBEDDING.value())
                : endpoints.resolve(model))
            .orElseThrow(() -> new EmbeddingException("no embedding model is configured"));

    spendGuard.authorize(SpendGuard.Kind.EMBEDDING, endpoint);
    Response response;
    try {
      response =
          restClientBuilder
              .clone()
              .build()
              .post()
              .uri(embeddingsUrl(endpoint.baseUrl()))
              .headers(h -> h.setBearerAuth(endpoint.apiKey()))
              .header("Content-Type", "application/json")
              .body(Map.of("model", endpoint.modelName(), "input", inputs))
              .retrieve()
              .body(Response.class);
    } catch (Exception e) {
      // The provider's message can echo the request; log the type only, never the inputs or key.
      logger.warn(
          "Embedding call to provider '{}' failed: {}",
          endpoint.providerName(),
          e.getClass().getSimpleName());
      throw new EmbeddingException("the embedding provider did not answer", e);
    }
    if (response == null || response.data() == null || response.data().size() != inputs.size()) {
      throw new EmbeddingException("the embedding provider answered an unexpected shape");
    }
    List<float[]> vectors = new ArrayList<>(inputs.size());
    response.data().stream()
        .sorted((a, b) -> Integer.compare(a.index(), b.index()))
        .forEach(
            item -> {
              float[] v = new float[item.embedding().size()];
              for (int i = 0; i < v.length; i++) {
                v[i] = item.embedding().get(i).floatValue();
              }
              vectors.add(v);
            });
    int tokens =
        response.usage() != null && response.usage().prompt_tokens() != null
            ? response.usage().prompt_tokens()
            : 0;
    return new EmbeddingResult(endpoint.modelName(), vectors, tokens);
  }

  /** {@code ai_providers} stores the host root; the OpenAI route needs {@code /v1}. */
  static String embeddingsUrl(String baseUrl) {
    String trimmed = baseUrl.replaceAll("/+$", "");
    return (trimmed.endsWith("/v1") || trimmed.contains("/v1/") ? trimmed : trimmed + "/v1")
        + "/embeddings";
  }
}
