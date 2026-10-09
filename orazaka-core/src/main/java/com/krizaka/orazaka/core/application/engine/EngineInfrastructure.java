package com.krizaka.orazaka.core.application.engine;

import com.krizaka.orazaka.core.application.pipeline.EnginePipelineBridge;
import com.krizaka.orazaka.core.application.pipeline.EngineStreamBridge;
import com.krizaka.orazaka.core.application.routing.DynamicChatModelFactory;
import com.krizaka.orazaka.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.orazaka.core.domain.ports.outbound.UserCredentialsProvider;
import java.util.Objects;

/**
 * Immutable parameter object grouping engine infrastructure dependencies.
 *
 * <p>Introduced to reduce the {@link AbstractEngine} constructor from 11 parameters to 7,
 * satisfying SonarQube rule {@code java:S107} (constructor parameter count ≤ 7).
 *
 * @param modelCatalogProvider Outbound port for querying available model metadata.
 * @param pipelineBridge The pipeline compilation bridge component.
 * @param streamBridge The reactive streaming bridge component.
 * @param credentialsProvider Outbound port for decrypted user API credentials lookup.
 * @param modelFactory Dynamic ChatModel factory for commercial providers.
 */
public record EngineInfrastructure(
    ModelCatalogProvider modelCatalogProvider,
    EnginePipelineBridge pipelineBridge,
    EngineStreamBridge streamBridge,
    UserCredentialsProvider credentialsProvider,
    DynamicChatModelFactory modelFactory) {

  public EngineInfrastructure {
    Objects.requireNonNull(pipelineBridge, "EnginePipelineBridge must not be null");
    Objects.requireNonNull(streamBridge, "EngineStreamBridge must not be null");
    Objects.requireNonNull(credentialsProvider, "UserCredentialsProvider must not be null");
    Objects.requireNonNull(modelFactory, "DynamicChatModelFactory must not be null");
  }
}
