package com.krizaka.orazaka.adapter.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.orazaka.adapter.springai.client.ChatGeneratorClientImpl;
import com.krizaka.orazaka.adapter.springai.client.EmbeddingClientImpl;
import com.krizaka.orazaka.core.application.engine.Engine;
import com.krizaka.orazaka.core.application.pipeline.PipelineOptionsRegistry;
import com.krizaka.orazaka.core.domain.ports.outbound.ChatGeneratorClient;
import com.krizaka.orazaka.core.domain.ports.outbound.EmbeddingClient;
import com.krizaka.orazaka.core.domain.ports.outbound.ModelEndpointResolver;
import com.krizaka.orazaka.core.infrastructure.spend.SpendGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The inference clients that left the core are wired here, unless the host brings its own. */
class SpringAiAdapterAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SpringAiAdapterAutoConfiguration.class))
          .withBean(Engine.class, () -> mock(Engine.class))
          .withBean(PipelineOptionsRegistry.class, () -> mock(PipelineOptionsRegistry.class))
          .withBean(ModelEndpointResolver.class, () -> mock(ModelEndpointResolver.class))
          .withBean(SpendGuard.class, SpendGuard::unlimited);

  @Test
  void theChatAndEmbeddingPortsAreWired() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(ChatGeneratorClient.class);
          assertThat(context)
              .getBean(ChatGeneratorClient.class)
              .isInstanceOf(ChatGeneratorClientImpl.class);
          assertThat(context).hasSingleBean(EmbeddingClient.class);
          assertThat(context)
              .getBean(EmbeddingClient.class)
              .isInstanceOf(EmbeddingClientImpl.class);
        });
  }

  @Test
  void aHostPortWins() {
    ChatGeneratorClient own = mock(ChatGeneratorClient.class);
    runner
        .withBean(ChatGeneratorClient.class, () -> own)
        .run(context -> assertThat(context).getBean(ChatGeneratorClient.class).isSameAs(own));
  }
}
