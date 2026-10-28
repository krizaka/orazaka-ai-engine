package com.krizaka.orazaka.adapter.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.orazaka.core.application.engine.Engine;
import com.krizaka.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.orazaka.core.application.pipeline.DynamicPipelineExecutor;
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
import com.krizaka.orazaka.core.domain.ports.outbound.ChatInvocation;
import com.krizaka.orazaka.core.domain.ports.outbound.DomainEventPublisher;
import com.krizaka.orazaka.core.domain.ports.outbound.ImageGeneratorClient;
import com.krizaka.orazaka.core.domain.ports.outbound.MemoryResolver;
import com.krizaka.orazaka.core.domain.ports.outbound.ModelCatalogProvider;
import com.krizaka.orazaka.core.domain.ports.outbound.PipelineConfigProvider;
import com.krizaka.orazaka.core.domain.ports.outbound.PromptAssembly;
import com.krizaka.orazaka.core.domain.ports.outbound.SemanticClassifierPort;
import com.krizaka.orazaka.core.domain.ports.outbound.SpeechSynthesis;
import com.krizaka.orazaka.core.domain.ports.outbound.StepRouteProvider;
import com.krizaka.orazaka.core.domain.ports.outbound.ToolRegistry;
import com.krizaka.orazaka.core.domain.ports.outbound.ToolResolution;
import com.krizaka.orazaka.core.domain.ports.outbound.UserCredentialsProvider;
import com.krizaka.orazaka.core.infrastructure.config.CoreProperties;
import com.krizaka.orazaka.core.infrastructure.config.SecurityProperties;
import com.krizaka.orazaka.core.infrastructure.host.HostCapabilityDetector;
import com.krizaka.orazaka.persistence.domain.ports.inbound.CatalogModelManager;
import com.krizaka.orazaka.persistence.domain.ports.inbound.ChatSessionPersistenceProvider;
import com.krizaka.orazaka.persistence.domain.ports.inbound.RuntimeConfigProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** What the core services' auto-configuration wires, and that a host's own bean wins. */
class CoreServicesAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CoreServicesAutoConfiguration.class))
          .withPropertyValues(
              "orazaka.core.knowledge.base-url=http://knowledge.invalid",
              "orazaka.core.knowledge.service-secret=0123456789abcdef0123456789abcdef",
              "spring.ai.ollama.base-url=http://ollama.invalid")
          .withBean(RestClient.Builder.class, RestClient::builder)
          .withBean(ChatInvocation.class, () -> mock(ChatInvocation.class))
          .withBean(SpeechSynthesis.class, () -> mock(SpeechSynthesis.class))
          .withBean(CoreProperties.class, () -> mock(CoreProperties.class))
          .withBean(SecurityProperties.class, () -> mock(SecurityProperties.class))
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
          .withBean(ModelCatalogProvider.class, () -> mock(ModelCatalogProvider.class))
          .withBean(ToolResolution.class, () -> mock(ToolResolution.class))
          .withBean(PromptAssembly.class, () -> mock(PromptAssembly.class))
          .withBean(UserCredentialsProvider.class, () -> mock(UserCredentialsProvider.class))
          .withBean(PipelineConfigProvider.class, () -> mock(PipelineConfigProvider.class))
          .withBean(PromptContextInterceptor.class, () -> mock(PromptContextInterceptor.class))
          .withBean(ChatGeneratorClient.class, () -> mock(ChatGeneratorClient.class))
          .withBean(ImageGeneratorClient.class, () -> mock(ImageGeneratorClient.class))
          .withBean(CatalogModelManager.class, () -> mock(CatalogModelManager.class))
          .withBean(
              ChatSessionPersistenceProvider.class,
              () -> mock(ChatSessionPersistenceProvider.class))
          .withBean(
              com.krizaka.orazaka.persistence.domain.ports.inbound.JobPersistenceProvider.class,
              () ->
                  mock(
                      com.krizaka.orazaka.persistence.domain.ports.inbound.JobPersistenceProvider
                          .class))
          .withBean(RuntimeConfigProvider.class, () -> mock(RuntimeConfigProvider.class))
          .withBean(ToolRegistry.class, () -> mock(ToolRegistry.class))
          .withBean(AccessAudit.class, () -> mock(AccessAudit.class))
          .withBean(SemanticClassifierPort.class, () -> mock(SemanticClassifierPort.class))
          .withBean(ObjectMapper.class, ObjectMapper::new);

  @Test
  void theCoreServicesAreWiredOnTheirPorts() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).getBean(AiClient.class).isInstanceOf(DefaultAiClient.class);
          assertThat(context)
              .getBean(AudioGeneratorClient.class)
              .isInstanceOf(
                  com.krizaka.orazaka.core.infrastructure.adapter.ai.DefaultAudioGeneratorClient
                      .class);
          assertThat(context)
              .getBean(CatalogModelService.class)
              .isInstanceOf(DefaultCatalogModelService.class);
          assertThat(context)
              .getBean(ChatSessionService.class)
              .isInstanceOf(DefaultChatSessionService.class);
          assertThat(context).getBean(McpService.class).isInstanceOf(DefaultMcpService.class);
          assertThat(context)
              .getBean(com.krizaka.orazaka.core.domain.ports.inbound.JobService.class)
              .isInstanceOf(com.krizaka.orazaka.core.application.service.DefaultJobService.class);
          assertThat(context).getBean(StepRouter.class).isInstanceOf(PolicyStepRouter.class);
          assertThat(context)
              .getBean(com.krizaka.orazaka.core.domain.ports.outbound.AudioTranscriber.class)
              .isInstanceOf(
                  com.krizaka.orazaka.adapter.springai.remote.WhisperTranscriptionClient.class);
          assertThat(context)
              .getBean(
                  com.krizaka.orazaka.core.domain.ports.outbound.InfrastructureStatusProvider.class)
              .isInstanceOf(com.krizaka.orazaka.adapter.springai.remote.InfrastructureProber.class);
          assertThat(context)
              .hasSingleBean(com.krizaka.orazaka.adapter.springai.spend.SpendGuard.class);
          assertThat(context).hasSingleBean(StepRouteProvider.class);
          assertThat(context).hasSingleBean(SemanticRoutingEngine.class);
          assertThat(context).hasSingleBean(HostCapabilityDetector.class);
          assertThat(context).hasSingleBean(Engine.class);
          assertThat(context).hasSingleBean(DynamicPipelineExecutor.class);
          assertThat(context).hasSingleBean(MemoryResolver.class);
          assertThat(context).hasSingleBean(DomainEventPublisher.class);
          assertThat(context)
              .hasSingleBean(com.krizaka.orazaka.core.domain.ports.outbound.KnowledgeService.class);
          assertThat(context)
              .hasSingleBean(
                  com.krizaka.orazaka.core.domain.ports.outbound.ModelEndpointResolver.class);
        });
  }

  @Test
  void aHostsOwnServiceReplacesTheDefault() {
    McpService own = mock(McpService.class);
    runner
        .withBean(McpService.class, () -> own)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpService.class);
              assertThat(context.getBean(McpService.class)).isSameAs(own);
            });
  }
}
