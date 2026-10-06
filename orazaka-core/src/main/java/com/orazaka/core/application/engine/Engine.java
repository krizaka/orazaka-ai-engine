package com.orazaka.core.application.engine;

import com.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.orazaka.core.application.pipeline.DynamicPipelineExecutor;
import com.orazaka.core.application.pipeline.EnginePipelineBridge;
import com.orazaka.core.application.pipeline.EngineStreamBridge;
import com.orazaka.core.application.routing.DynamicChatModelFactory;
import com.orazaka.core.domain.ports.outbound.ModelCatalogProvider;
import com.orazaka.core.domain.ports.outbound.UserCredentialsProvider;
import com.orazaka.core.infrastructure.config.CoreProperties;
import java.util.List;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Concrete, final implementation of {@link AbstractEngine}.
 *
 * <p>This is the sole permitted subclass of the sealed {@link AbstractEngine} hierarchy. It
 * delegates all orchestration logic to the abstract parent and serves as the default production
 * engine.
 */
@Component
public final class Engine extends AbstractEngine {

  /**
   * Primary constructor for Engine with direct Spring AI dependency injection.
   *
   * @param chatModel The primary chat model bean.
   * @param ttsModel The primary text-to-speech model bean.
   * @param properties Core engine configuration properties.
   * @param interceptors Ordered list of context interceptors.
   * @param orchestrationPipeline The orchestration pipeline (nullable).
   * @param eventPublisher Spring event publisher for domain events.
   * @param modelCatalogProvider The model catalog provider to resolve active models dynamically.
   * @param pipelineBridge The pipeline compilation bridge component.
   * @param streamBridge The reactive streaming bridge component.
   * @param credentialsProvider Outbound port for credentials lookup.
   * @param modelFactory Dynamic ChatModel factory.
   */
  public Engine(
      ChatModel chatModel,
      TextToSpeechModel ttsModel,
      CoreProperties properties,
      List<PromptContextInterceptor> interceptors,
      DynamicPipelineExecutor pipelineExecutor,
      ApplicationEventPublisher eventPublisher,
      ModelCatalogProvider modelCatalogProvider,
      EnginePipelineBridge pipelineBridge,
      EngineStreamBridge streamBridge,
      UserCredentialsProvider credentialsProvider,
      DynamicChatModelFactory modelFactory) {
    super(
        chatModel,
        ttsModel,
        properties != null ? properties.defaultProvider() : null,
        interceptors,
        pipelineExecutor,
        eventPublisher,
        new EngineInfrastructure(
            modelCatalogProvider, pipelineBridge, streamBridge, credentialsProvider, modelFactory));
  }
}
