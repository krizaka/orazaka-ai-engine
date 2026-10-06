package com.orazaka.interceptor.reformulation;

import com.orazaka.core.application.pipeline.PipelineOptionsRegistry;
import com.orazaka.core.infrastructure.config.CoreProperties;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

/**
 * Spring Boot auto-configuration for the reformulation interceptor module.
 *
 * <p>Conditionally registers {@link RefinerInterceptor} and {@link RouterInterceptor} beans only
 * when their dependencies ({@link ChatModel} map, {@link CoreProperties}, {@link
 * PipelineOptionsRegistry}) are present in the application context.
 *
 * @since 1.0.0
 */
@AutoConfiguration
public class ReformulationAutoConfiguration {

  /**
   * Registers the {@link RefinerInterceptor} bean.
   *
   * @param chatModels Provider-keyed map of ChatModel beans.
   * @param properties Core configuration properties.
   * @param optionsRegistry Pipeline options strategy registry.
   * @param systemRefinementResource Refinement system prompt template.
   * @param contextEnvelopeResource Context envelope user prompt template.
   * @return The refiner interceptor.
   */
  @Bean
  @ConditionalOnBean(PipelineOptionsRegistry.class)
  public RefinerInterceptor refinerInterceptor(
      Map<String, ChatModel> chatModels,
      CoreProperties properties,
      PipelineOptionsRegistry optionsRegistry,
      @Value("classpath:/prompts/system-refinement.st") Resource systemRefinementResource,
      @Value("classpath:/prompts/context-envelope.st") Resource contextEnvelopeResource) {
    return new RefinerInterceptor(
        chatModels, properties, optionsRegistry, systemRefinementResource, contextEnvelopeResource);
  }

  /**
   * Registers the {@link RouterInterceptor} bean.
   *
   * @param chatModels Provider-keyed map of ChatModel beans.
   * @param properties Core configuration properties.
   * @param optionsRegistry Pipeline options strategy registry.
   * @param routerSystemResource Router system prompt template.
   * @return The router interceptor.
   */
  @Bean
  @ConditionalOnBean(PipelineOptionsRegistry.class)
  public RouterInterceptor routerInterceptor(
      Map<String, ChatModel> chatModels,
      CoreProperties properties,
      PipelineOptionsRegistry optionsRegistry,
      @Value("classpath:/prompts/system-router.st") Resource routerSystemResource) {
    return new RouterInterceptor(chatModels, properties, optionsRegistry, routerSystemResource);
  }

  /**
   * Registers the {@link SemanticRouterInterceptor} bean.
   *
   * <p>Evaluates multi-intent requests and decomposes them into SIM-DAG dependency graphs for
   * sequential or parallel multimedia task orchestration.
   *
   * @return The SIM-DAG router interceptor.
   */
  @Bean
  SemanticRouterInterceptor semanticRouterInterceptor() {
    return new SemanticRouterInterceptor();
  }
}
