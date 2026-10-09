package com.krizaka.orazaka.business;

import com.krizaka.orazaka.business.api.UseCase;
import com.krizaka.orazaka.business.api.UseCaseDispatcher;
import com.krizaka.orazaka.business.api.UseCaseRegistry;
import com.krizaka.orazaka.business.application.SpringUseCaseRegistry;
import com.krizaka.orazaka.business.application.UseCaseDispatcherImpl;
import com.krizaka.orazaka.business.prompt.MarkdownPromptResolver;
import com.krizaka.orazaka.business.usecases.chat.ChatAssistantUseCase;
import com.krizaka.orazaka.business.usecases.image.ImageGenerationUseCase;
import com.krizaka.orazaka.business.usecases.studio.StudioRunUseCase;
import com.krizaka.orazaka.core.domain.ports.inbound.AiClient;
import com.krizaka.orazaka.studio.domain.port.StudioRunClient;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

/**
 * Spring Boot auto-configuration for the orazaka-business module — the App Factory wiring.
 *
 * <p>Registers the prompt resolver, every {@link UseCase} bean, and the {@link UseCaseRegistry} /
 * {@link UseCaseDispatcher}. Beans are declared explicitly (no classpath scanning) so the App
 * Factory wires identically in both host apps (router and worker), which scan different packs.
 * Adding a use-case = a new {@code UseCase} class + one {@code @Bean} line here — no core/router
 * change.
 *
 * <p>Discovered at runtime via {@code META-INF/spring/AutoConfiguration.imports}.
 *
 * @since 1.0.0
 */
@AutoConfiguration
public class BusinessAutoConfiguration {

  @Bean
  MarkdownPromptResolver markdownPromptResolver(ResourceLoader resourceLoader) {
    return new MarkdownPromptResolver(resourceLoader);
  }

  @Bean
  ChatAssistantUseCase chatAssistantUseCase(AiClient aiClient, MarkdownPromptResolver prompts) {
    return new ChatAssistantUseCase(aiClient, prompts);
  }

  @Bean
  ImageGenerationUseCase imageGenerationUseCase(AiClient aiClient) {
    return new ImageGenerationUseCase(aiClient);
  }

  /**
   * Registers the Studio run use-case (ADR-034 §9.2).
   *
   * <p>Always wired: the port is always satisfied, by the HTTP adapter when the marketplace is
   * deployed and by a no-op that refuses runs when it is not. A conditional bean here would put the
   * "is there a marketplace" branch back into every call site (ERR-127).
   *
   * @param studioRunClient the Tier-1 port, adapter chosen at bootstrap by orazaka-studio-client
   * @return the use-case that makes a Studio run a first-class Intention
   */
  @Bean
  StudioRunUseCase studioRunUseCase(StudioRunClient studioRunClient) {
    return new StudioRunUseCase(studioRunClient);
  }

  @Bean
  UseCaseRegistry useCaseRegistry(List<UseCase<?, ?>> useCases) {
    return new SpringUseCaseRegistry(useCases);
  }

  @Bean
  UseCaseDispatcher useCaseDispatcher(UseCaseRegistry useCaseRegistry) {
    return new UseCaseDispatcherImpl(useCaseRegistry);
  }
}
