package com.krizaka.orazaka.core.infrastructure.config;

import com.krizaka.orazaka.core.application.engine.GraphEngine;
import com.krizaka.orazaka.core.application.processing.AudioPreProcessor;
import com.krizaka.orazaka.core.application.processing.VideoPreProcessor;
import com.krizaka.orazaka.core.domain.ports.outbound.AdminRegistry;
import com.krizaka.orazaka.core.domain.ports.outbound.CapabilityProvider;
import com.krizaka.orazaka.core.domain.ports.outbound.InfrastructureStatusProvider;
import com.krizaka.orazaka.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.orazaka.core.infrastructure.adapter.processor.LocalAudioProcessor;
import com.krizaka.orazaka.core.infrastructure.adapter.processor.LocalVideoProcessor;
import com.krizaka.orazaka.core.infrastructure.adapter.processor.ProviderEndpointResolver;
import com.krizaka.orazaka.core.infrastructure.adapter.processor.WhisperTranscriptionClient;
import com.krizaka.orazaka.persistence.domain.ports.inbound.CatalogModelManager;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Boot {@code @Configuration} class for the {@code orazaka-core} module.
 *
 * <p>Defines configuration properties ({@link CoreProperties}), the {@link GraphEngine}, and
 * infrastructure beans. AI model beans (chat/speech) are DB-driven routing beans in the {@code
 * infrastructure.provider} package (ADR-030: {@code RoutedChatModel} / {@code
 * RoutedTextToSpeechModel} backed by {@code DynamicModelRouter}), not static wiring here.
 *
 * @see CoreProperties
 */
@Configuration
public class CoreConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(CoreConfiguration.class);

  private static final String DEFAULT_PROVIDER = "ollama";
  private static final String DEFAULT_REFINER_MODEL = "llama3.2:3b";

  /** Nested configuration record for custom image models. */
  public static record CustomImageConfig(String provider, String baseUrl) {}

  /** Nested configuration record for custom video models. */
  public static record CustomVideoConfig(String provider, String baseUrl) {}

  /**
   * Binds the {@code orazaka.core} configuration prefix to a {@link CoreProperties} record.
   *
   * @param env The Spring environment for property resolution.
   * @return The bound CoreProperties instance.
   * @throws IllegalStateException If the configuration prefix is missing or binding fails.
   */
  @Bean
  public CoreProperties coreProperties(Environment env) {
    CoreProperties temp = null;
    try {
      temp = Binder.get(env).bind("orazaka.core", CoreProperties.class).orElse(null);
    } catch (RuntimeException e) {
      logger.debug("Could not bind 'orazaka.core' properties, using defaults.", e);
    }

    String defaultProvider =
        (temp != null && temp.defaultProvider() != null)
            ? temp.defaultProvider()
            : DEFAULT_PROVIDER;

    CoreProperties.RagConfig rag =
        (temp != null && temp.rag() != null)
            ? temp.rag()
            : new CoreProperties.RagConfig(true, "pgvector", 3);

    CoreProperties.McpConfig mcp =
        (temp != null && temp.mcp() != null) ? temp.mcp() : new CoreProperties.McpConfig(List.of());

    // Master pipeline switch + routing come from yaml; per-interceptor enable is DB-driven
    // (pipeline_interceptor_config). The refiner/router model wiring is sourced from defaults
    // here, not yaml — keeping orchestration's refiner()/router() non-null for the AI stages.
    boolean orchestrationEnabled =
        (temp == null || temp.orchestration() == null) || temp.orchestration().enabled();
    CoreProperties.RoutingConfig routing =
        (temp != null && temp.orchestration() != null && temp.orchestration().routing() != null)
            ? temp.orchestration().routing()
            : new CoreProperties.RoutingConfig(null, null);
    CoreProperties.OrchestrationConfig orchestration =
        new CoreProperties.OrchestrationConfig(
            orchestrationEnabled,
            new CoreProperties.InterceptorConfig(DEFAULT_PROVIDER, DEFAULT_REFINER_MODEL, 0.7),
            new CoreProperties.InterceptorConfig(DEFAULT_PROVIDER, DEFAULT_REFINER_MODEL, 0.0),
            routing);

    CoreProperties.VideoGenerationConfig videoGen =
        new CoreProperties.VideoGenerationConfig(
            "localai-video", "http://" + "local" + "host:8188");
    CoreProperties.VideoAnalysisConfig videoAnalysis = new CoreProperties.VideoAnalysisConfig(8, 5);
    CoreProperties.VideoConfig videoConfig =
        new CoreProperties.VideoConfig(videoAnalysis, videoGen);

    // Default ImageConfig with defaults
    // Bound values first, the literals only where the yaml says nothing (ADR-062). This block used
    // to
    // be literals alone and never read temp.image(): `steps` — what IMAGE_STEP prices a generation
    // on
    // — was 20 whatever IMAGE_GEN_STEPS said, so a 10-step deployment billed double what it did.
    // That was cause 2 of 2; cause 1 was a second constructor that stopped the binder building
    // this.
    CoreProperties.ImageGenerationConfig bound =
        temp != null && temp.image() != null ? temp.image().generation() : null;
    CoreProperties.ImageGenerationConfig imageGen =
        new CoreProperties.ImageGenerationConfig(
            orDefault(bound == null ? null : bound.provider(), "localai-image"),
            orDefault(bound == null ? null : bound.baseUrl(), "http://" + "local" + "host:8086"),
            orDefault(bound == null ? null : bound.apiKey(), "not-required"),
            orDefault(bound == null ? null : bound.model(), "stable-diffusion"),
            orDefault(bound == null ? null : bound.width(), 512),
            orDefault(bound == null ? null : bound.height(), 512),
            orDefault(bound == null ? null : bound.n(), 1),
            // stable-diffusion.cpp's own default; the deployment states it via IMAGE_GEN_STEPS.
            orDefault(bound == null ? null : bound.steps(), 20),
            orDefault(bound == null ? null : bound.connectTimeoutMs(), 180000),
            orDefault(bound == null ? null : bound.readTimeoutMs(), 180000));
    CoreProperties.ImageConfig imageConfig = new CoreProperties.ImageConfig(imageGen);

    CoreProperties.VisionConfig vision =
        new CoreProperties.VisionConfig(DEFAULT_PROVIDER, "llama3.2-vision:latest");
    CoreProperties.AudioConfig audio =
        new CoreProperties.AudioConfig(DEFAULT_PROVIDER, DEFAULT_REFINER_MODEL, "whisper-1");

    return new CoreProperties(
        defaultProvider, rag, mcp, orchestration, videoConfig, imageConfig, vision, audio);
  }

  /**
   * Registers the shared {@link WhisperTranscriptionClient} bean for audio/video transcription.
   *
   * <p>Uses Spring {@link RestClient} with {@link org.springframework.core.io.ByteArrayResource}
   * for multipart file uploads — zero manual boundary forging.
   *
   * @param restClientBuilder The auto-configured RestClient builder.
   * @param objectMapper The auto-configured Jackson ObjectMapper.
   * @return A configured WhisperTranscriptionClient instance.
   */
  @Bean
  public WhisperTranscriptionClient whisperTranscriptionClient(
      RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
    return new WhisperTranscriptionClient(restClientBuilder, objectMapper);
  }

  /**
   * Registers the VideoPreProcessor bean.
   *
   * @param properties Core configuration properties.
   * @param catalogModelManager The catalog model manager.
   * @param whisperClient The shared Whisper transcription client.
   * @return A VideoPreProcessor instance.
   */
  @Bean
  public VideoPreProcessor videoPreProcessor(
      CoreProperties properties,
      ProviderEndpointResolver providerEndpointResolver,
      WhisperTranscriptionClient whisperClient) {
    return new LocalVideoProcessor(properties, providerEndpointResolver, whisperClient);
  }

  /**
   * Registers the AudioPreProcessor bean.
   *
   * @param properties Core configuration properties.
   * @param providerEndpointResolver Resolver for provider base URLs.
   * @param whisperClient The shared Whisper transcription client.
   * @return An AudioPreProcessor instance.
   */
  @Bean
  public AudioPreProcessor audioPreProcessor(
      CoreProperties properties,
      ProviderEndpointResolver providerEndpointResolver,
      WhisperTranscriptionClient whisperClient) {
    return new LocalAudioProcessor(properties, providerEndpointResolver, whisperClient);
  }

  /**
   * Registers the {@link ProviderEndpointResolver} bean, owning the catalog dependency used to
   * resolve provider base URLs for the local processors.
   *
   * @param catalogModelManager The catalog model manager.
   * @return A ProviderEndpointResolver instance.
   */
  @Bean
  public ProviderEndpointResolver providerEndpointResolver(
      CatalogModelManager catalogModelManager) {
    return new ProviderEndpointResolver(catalogModelManager);
  }

  /**
   * Creates the singleton {@link AdminRegistry} for runtime capability lock management.
   *
   * @return A new thread-safe admin registry instance.
   */
  @Bean
  public AdminRegistry adminRegistry() {
    return new AdminRegistry();
  }

  /**
   * Fallback Jackson 3 {@link ObjectMapper} bean. Spring Boot 4 auto-configures a Jackson 3 {@code
   * JsonMapper} (an {@code ObjectMapper} subtype) in full application contexts; this conditional
   * bean only materialises in slim contexts where that auto-configuration is absent, so adapters
   * that inject an {@code ObjectMapper} (e.g. {@code WhisperTranscriptionClient}, the router's
   * {@code McpController} / {@code JobListener}) always resolve one.
   *
   * @return A shared Jackson 3 ObjectMapper.
   */
  @Bean
  @Conditional(OnMissingObjectMapperCondition.class)
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  /**
   * Creates the {@link GraphEngine} bean for SDUI Operation Graph compilation.
   *
   * @param capabilityProvider Database-backed source of capability descriptors (blueprint + enabled
   *     state). Resolved lazily; absent in contexts without the persistence adapter.
   * @param adminRegistry Runtime lock registry for dynamic capability control.
   * @param prober Asynchronous infrastructure status prober.
   * @param modelCatalogProvider Resolves the active chat model dynamically.
   * @return A configured graph engine instance.
   */
  @Bean
  public GraphEngine graphEngine(
      ObjectProvider<CapabilityProvider> capabilityProvider,
      AdminRegistry adminRegistry,
      InfrastructureStatusProvider prober,
      ModelCatalogProvider modelCatalogProvider) {
    return new GraphEngine(
        capabilityProvider.getIfAvailable(), adminRegistry, prober, modelCatalogProvider);
  }

  /** The bound value when the yaml gave one, the documented default when it did not. */
  private static <T> T orDefault(T bound, T fallback) {
    return bound != null ? bound : fallback;
  }
}
