package com.krizaka.orazaka.interceptor.validation;

import com.krizaka.orazaka.core.domain.ports.outbound.TestShaperPort;
import com.krizaka.orazaka.core.domain.ports.outbound.ValidationPipelineRepository;
import java.util.Map;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the validation interceptor module.
 *
 * <p>Registers the {@link CostShieldInterceptor}, the {@link SpringAiTestShaperAdapter} (Tier D),
 * and the {@link ClosedLoopValidationInterceptor}. The advisor is conditionally activated via
 * {@code orazaka.interceptor.validation.closed-loop.enabled}.
 *
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(ClosedLoopValidationProperties.class)
public class ValidationAutoConfiguration {

  /**
   * Registers the {@link CostShieldInterceptor} as a Spring bean.
   *
   * @return The cost shield interceptor instance.
   */
  /**
   * The crisis guard, registered unconditionally.
   *
   * <p>No {@code @ConditionalOnProperty} and no toggle: a safety control that a property can remove
   * is one that will be removed, in the deployment that most needs it. It is inert for a pack that
   * declares no crisis terms, which is every pack below {@code REGULATED} (ADR-055 §4).
   *
   * @return the interceptor
   */
  @Bean
  public SafetyInterceptor safetyInterceptor() {
    return new SafetyInterceptor();
  }

  @Bean
  public CostShieldInterceptor costShieldInterceptor() {
    return new CostShieldInterceptor();
  }

  /**
   * Registers the {@link SpringAiTestShaperAdapter} bean for Tier D (Test-Driven Response).
   *
   * <p>Activated only when at least one ChatModel bean is available in the context. Uses the first
   * available ChatModel from the provider-keyed map.
   *
   * @param chatModels Provider-keyed ChatModel map.
   * @return The test shaper adapter.
   */
  @Bean
  @ConditionalOnBean(ChatModel.class)
  @ConditionalOnProperty(
      prefix = "orazaka.interceptor.validation.closed-loop",
      name = "tdr-enabled",
      havingValue = "true",
      matchIfMissing = false)
  public SpringAiTestShaperAdapter springAiTestShaperAdapter(Map<String, ChatModel> chatModels) {
    ChatModel tdrModel = chatModels.values().stream().findFirst().orElse(null);
    if (tdrModel == null) {
      throw new IllegalStateException(
          "Tier D (TDR) enabled but no ChatModel available in context.");
    }
    return new SpringAiTestShaperAdapter(tdrModel);
  }

  /**
   * Registers the {@link ClosedLoopValidationInterceptor} bean.
   *
   * <p>Activated only when {@code orazaka.interceptor.validation.closed-loop.enabled=true}. Tier C
   * (Semantic Debate) and Tier D (TDR) gracefully degrade when their dependencies are absent.
   *
   * @param properties Closed-loop validation configuration.
   * @param chatModels Provider-keyed ChatModel map (may be empty).
   * @param validationPipelineRepository Dynamic pipeline config repository (nullable).
   * @param testShaperPort Tier D assertion generator (nullable).
   * @return The quantum validation advisor.
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "orazaka.interceptor.validation.closed-loop",
      name = "enabled",
      havingValue = "true")
  public ClosedLoopValidationInterceptor closedLoopValidationInterceptor(
      ClosedLoopValidationProperties properties,
      Map<String, ChatModel> chatModels,
      @Autowired(required = false) ValidationPipelineRepository validationPipelineRepository,
      @Autowired(required = false) TestShaperPort testShaperPort) {
    ChatModel debateModel = chatModels.values().stream().findFirst().orElse(null);
    return new ClosedLoopValidationInterceptor(
        properties, debateModel, validationPipelineRepository, testShaperPort);
  }
}
