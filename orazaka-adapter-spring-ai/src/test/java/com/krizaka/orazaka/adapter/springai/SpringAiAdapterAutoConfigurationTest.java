package com.krizaka.orazaka.adapter.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.orazaka.adapter.springai.client.ChatGeneratorClientImpl;
import com.krizaka.orazaka.adapter.springai.client.EmbeddingClientImpl;
import com.krizaka.orazaka.adapter.springai.provider.SpringAiDynamicChatModelFactory;
import com.krizaka.orazaka.adapter.springai.routing.DynamicModelRouter;
import com.krizaka.orazaka.adapter.springai.routing.RoutedChatModel;
import com.krizaka.orazaka.adapter.springai.routing.RoutedTextToSpeechModel;
import com.krizaka.orazaka.adapter.springai.routing.UniversalProxyChatProvider;
import com.krizaka.orazaka.core.application.engine.Engine;
import com.krizaka.orazaka.core.application.pipeline.PipelineOptionsRegistry;
import com.krizaka.orazaka.core.application.routing.DynamicChatModelFactory;
import com.krizaka.orazaka.core.domain.ports.outbound.ChatGeneratorClient;
import com.krizaka.orazaka.core.domain.ports.outbound.EmbeddingClient;
import com.krizaka.orazaka.core.domain.ports.outbound.ModelEndpointResolver;
import com.krizaka.orazaka.core.infrastructure.spend.SpendGuard;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * What the adapter wires: the inference clients and the routing cluster, unless the host brings its
 * own.
 */
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

  @Test
  void theRoutingClusterIsRegisteredAsThePrimaryModels() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(UniversalProxyChatProvider.class);
          assertThat(context).hasSingleBean(DynamicModelRouter.class);
          assertThat(context.getBean(ChatModel.class)).isInstanceOf(RoutedChatModel.class);
          assertThat(context.getBean(TextToSpeechModel.class))
              .isInstanceOf(RoutedTextToSpeechModel.class);
        });
  }

  @Test
  void theRoutingClusterBacksOffForTheRouterTheHostBrings() {
    var own = mock(DynamicModelRouter.class);
    runner
        .withBean(DynamicModelRouter.class, () -> own)
        .run(context -> assertThat(context.getBean(DynamicModelRouter.class)).isSameAs(own));
  }

  @Test
  void theDynamicChatModelFactoryIsWiredOverTheThreeProviders() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(DynamicChatModelFactory.class);
          assertThat(context.getBean(DynamicChatModelFactory.class))
              .isInstanceOf(SpringAiDynamicChatModelFactory.class);
          var model = context.getBean(DynamicChatModelFactory.class);
          assertThat(model.createChatModel("anthropic", "claude-x", "k")).isNotNull();
          assertThat(model.createChatModel("gemini", "gemini-x", "k")).isNotNull();
          assertThat(model.createChatModel("openai", "gpt-x", "k")).isNotNull();
        });
  }

  @Test
  void aHostFactoryWins() {
    var own = mock(DynamicChatModelFactory.class);
    runner
        .withBean(DynamicChatModelFactory.class, () -> own)
        .run(context -> assertThat(context.getBean(DynamicChatModelFactory.class)).isSameAs(own));
  }
}
