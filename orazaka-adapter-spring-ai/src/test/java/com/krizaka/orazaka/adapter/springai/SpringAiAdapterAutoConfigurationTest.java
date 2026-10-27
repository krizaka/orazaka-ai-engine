package com.krizaka.orazaka.adapter.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.krizaka.orazaka.adapter.springai.routing.DynamicModelRouter;
import com.krizaka.orazaka.adapter.springai.routing.RoutedChatModel;
import com.krizaka.orazaka.adapter.springai.routing.RoutedTextToSpeechModel;
import com.krizaka.orazaka.adapter.springai.routing.UniversalProxyChatProvider;
import com.krizaka.orazaka.core.application.pipeline.PipelineOptionsRegistry;
import com.krizaka.orazaka.core.domain.ports.outbound.ModelEndpointResolver;
import com.krizaka.orazaka.core.infrastructure.spend.SpendGuard;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The routing cluster is registered by the adapter's auto-configuration, not by component scanning.
 */
class SpringAiAdapterAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SpringAiAdapterAutoConfiguration.class))
          .withBean(ModelEndpointResolver.class, () -> mock(ModelEndpointResolver.class))
          .withBean(PipelineOptionsRegistry.class, () -> mock(PipelineOptionsRegistry.class))
          .withBean(SpendGuard.class, SpendGuard::unlimited);

  @Test
  void registersTheRoutingClusterAsThePrimaryModels() {
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
  void backsOffForTheRouterTheHostBrings() {
    var own = mock(DynamicModelRouter.class);
    runner
        .withBean(DynamicModelRouter.class, () -> own)
        .run(context -> assertThat(context.getBean(DynamicModelRouter.class)).isSameAs(own));
  }
}
