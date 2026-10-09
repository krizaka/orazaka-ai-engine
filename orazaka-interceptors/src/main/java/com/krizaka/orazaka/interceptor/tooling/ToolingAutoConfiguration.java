package com.krizaka.orazaka.interceptor.tooling;

import com.krizaka.orazaka.core.domain.ports.outbound.ToolRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the tooling interceptor module.
 *
 * <p>Conditionally registers the {@link ToolInterceptor} bean only when a {@link ToolRegistry} is
 * present in the application context.
 *
 * @since 1.0.0
 */
@AutoConfiguration
public class ToolingAutoConfiguration {

  @Bean
  @ConditionalOnBean(ToolRegistry.class)
  ToolInterceptor toolInterceptor(ToolRegistry toolRegistry) {
    return new ToolInterceptor(toolRegistry);
  }
}
