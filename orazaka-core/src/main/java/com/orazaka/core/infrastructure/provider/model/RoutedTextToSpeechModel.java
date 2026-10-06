package com.orazaka.core.infrastructure.provider.model;

import com.orazaka.core.domain.model.MediaCategory;
import com.orazaka.core.domain.model.ModelEndpoint;
import com.orazaka.core.domain.ports.outbound.ModelEndpointResolver;
import com.orazaka.core.infrastructure.support.CoreException;
import java.util.Objects;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Primary {@link TextToSpeechModel} bean, backed by the DB-driven speech provider (ADR-030).
 *
 * <p>Replaces the former static {@code AiModelConfiguration.activeTtsModel} bean (built from {@code
 * spring.ai.localai.*}). The speech provider's endpoint is resolved from the DB ({@code
 * orazaka_models} {@code is_default} for category {@code speech} → {@code ai_providers} base-url)
 * and an OpenAI-compatible {@link OpenAiAudioSpeechModel} is built against it. The actual
 * voice/model carried on the {@link TextToSpeechPrompt} options drives synthesis at call time.
 *
 * @see CatalogModelEndpointResolver
 */
@Component
@Primary
public class RoutedTextToSpeechModel implements TextToSpeechModel {

  private final ModelEndpointResolver resolver;

  public RoutedTextToSpeechModel(ModelEndpointResolver resolver) {
    this.resolver = Objects.requireNonNull(resolver, "ModelEndpointResolver cannot be null");
  }

  @Override
  public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
    return speechModel().call(prompt);
  }

  @Override
  public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
    return speechModel().stream(prompt);
  }

  private TextToSpeechModel speechModel() {
    ModelEndpoint endpoint =
        resolver
            .resolveDefault(MediaCategory.SPEECH.value())
            .orElseThrow(
                () -> new CoreException("No speech model endpoint resolved (category=speech)."));
    // Spring AI 2.0 derives the com.openai client from the options via OpenAiSetup, so the speech
    // provider endpoint (base-url + api-key) is set on the OpenAiAudioSpeechOptions.
    return OpenAiAudioSpeechModel.builder()
        .options(
            OpenAiAudioSpeechOptions.builder()
                .model(endpoint.modelName())
                .apiKey(endpoint.apiKey())
                .baseUrl(endpoint.baseUrl())
                .build())
        .build();
  }
}
