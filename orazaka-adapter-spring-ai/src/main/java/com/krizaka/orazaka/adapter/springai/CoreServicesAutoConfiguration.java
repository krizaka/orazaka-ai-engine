package com.krizaka.orazaka.adapter.springai;

import com.krizaka.orazaka.core.application.engine.Engine;
import com.krizaka.orazaka.core.application.routing.PolicyStepRouter;
import com.krizaka.orazaka.core.application.routing.SemanticRoutingEngine;
import com.krizaka.orazaka.core.application.service.DefaultAiClient;
import com.krizaka.orazaka.core.application.service.DefaultCatalogModelService;
import com.krizaka.orazaka.core.application.service.DefaultChatSessionService;
import com.krizaka.orazaka.core.application.service.DefaultMcpService;
import com.krizaka.orazaka.core.domain.ports.inbound.AiClient;
import com.krizaka.orazaka.core.domain.ports.inbound.CatalogModelService;
import com.krizaka.orazaka.core.domain.ports.inbound.ChatSessionService;
import com.krizaka.orazaka.core.domain.ports.inbound.McpService;
import com.krizaka.orazaka.core.domain.ports.inbound.StepRouter;
import com.krizaka.orazaka.core.domain.ports.outbound.AccessAudit;
import com.krizaka.orazaka.core.domain.ports.outbound.AudioGeneratorClient;
import com.krizaka.orazaka.core.domain.ports.outbound.ChatGeneratorClient;
import com.krizaka.orazaka.core.domain.ports.outbound.ImageGeneratorClient;
import com.krizaka.orazaka.core.domain.ports.outbound.SemanticClassifierPort;
import com.krizaka.orazaka.core.domain.ports.outbound.StepRouteProvider;
import com.krizaka.orazaka.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.orazaka.core.infrastructure.adapter.ai.DefaultAudioGeneratorClient;
import com.krizaka.orazaka.core.infrastructure.adapter.routing.RuntimeStepRouteProvider;
import com.krizaka.orazaka.core.infrastructure.host.HostCapabilityDetector;
import com.krizaka.orazaka.persistence.domain.ports.inbound.CatalogModelManager;
import com.krizaka.orazaka.persistence.domain.ports.inbound.ChatSessionPersistenceProvider;
import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring wiring of the core services (ADR-092 §4 rule 1, orazaka-ai-engine#121): the core classes
 * are plain Java, registered here unless the host brings its own bean of the same type.
 */
@AutoConfiguration(after = SpringAiAdapterAutoConfiguration.class)
public class CoreServicesAutoConfiguration {

  /**
   * The client of the platform: chat, audio and image behind one port.
   *
   * @param chat the chat generator
   * @param audio the audio generator
   * @param image the image generator
   * @return the client
   */
  @Bean
  @ConditionalOnMissingBean(AiClient.class)
  public DefaultAiClient aiClient(
      ChatGeneratorClient chat, AudioGeneratorClient audio, ImageGeneratorClient image) {
    return new DefaultAiClient(chat, audio, image);
  }

  /**
   * The audio generation port, over the engine.
   *
   * @param engine the engine
   * @return the client
   */
  @Bean
  @ConditionalOnMissingBean(AudioGeneratorClient.class)
  public DefaultAudioGeneratorClient audioGeneratorClient(Engine engine) {
    return new DefaultAudioGeneratorClient(engine);
  }

  /**
   * The catalogue of models, read through the persistence port.
   *
   * @param manager the catalogue manager
   * @return the service
   */
  @Bean
  @ConditionalOnMissingBean(CatalogModelService.class)
  public DefaultCatalogModelService catalogModelService(CatalogModelManager manager) {
    return new DefaultCatalogModelService(manager);
  }

  /**
   * The chat sessions, read through the persistence port.
   *
   * @param provider the persistence provider
   * @return the service
   */
  @Bean
  @ConditionalOnMissingBean(ChatSessionService.class)
  public DefaultChatSessionService chatSessionService(ChatSessionPersistenceProvider provider) {
    return new DefaultChatSessionService(provider);
  }

  /**
   * The MCP tool service (ADR-091: roles and denials are audited at the tool).
   *
   * @param registry the tool registry
   * @param audit where a refusal is written
   * @return the service
   */
  @Bean
  @ConditionalOnMissingBean(McpService.class)
  public DefaultMcpService mcpService(ToolRegistry registry, AccessAudit audit) {
    return new DefaultMcpService(registry, audit);
  }

  /**
   * The routes of a step, read from the runtime configuration and the catalogue.
   *
   * @param config the runtime configuration
   * @param catalog the catalogue manager
   * @param mapper the JSON mapper
   * @return the provider
   */
  @Bean
  @ConditionalOnMissingBean(StepRouteProvider.class)
  public RuntimeStepRouteProvider stepRouteProvider(
      RuntimeConfigProvider config, CatalogModelManager catalog, ObjectMapper mapper) {
    return new RuntimeStepRouteProvider(config, catalog, mapper);
  }

  /**
   * The router that picks a step's model by policy.
   *
   * @param routes the routes of a step
   * @return the router
   */
  @Bean
  @ConditionalOnMissingBean(StepRouter.class)
  public PolicyStepRouter stepRouter(StepRouteProvider routes) {
    return new PolicyStepRouter(routes);
  }

  /**
   * The semantic routing engine, over the classifier port.
   *
   * @param classifier the classifier
   * @return the engine
   */
  @Bean
  @ConditionalOnMissingBean
  public SemanticRoutingEngine semanticRoutingEngine(SemanticClassifierPort classifier) {
    return new SemanticRoutingEngine(classifier);
  }

  /**
   * What the host machine can run (Apple Silicon or CPU only).
   *
   * @return the detector
   */
  @Bean
  @ConditionalOnMissingBean
  public HostCapabilityDetector hostCapabilityDetector() {
    return new HostCapabilityDetector();
  }
}
