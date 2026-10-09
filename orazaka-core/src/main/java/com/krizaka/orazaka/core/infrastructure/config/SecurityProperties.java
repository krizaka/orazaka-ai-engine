package com.krizaka.orazaka.core.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Security governance configuration properties. Maps to {@code orazaka.security} in {@code
 * application.yml}.
 *
 * <p>The {@code disableAi} property acts as a hard governance kill-switch. When set to {@code
 * true}, the {@code DynamicPipelineExecutor} will throw a {@link SecurityException} if any
 * interceptor returning {@code isAiDependent() == true} is invoked.
 *
 * @param disableAi When {@code true}, blocks all AI-dependent interceptors with a hard {@link
 *     SecurityException}. This is a compliance shield, not a performance failover.
 */
@ConfigurationProperties(prefix = "orazaka.security")
public record SecurityProperties(@DefaultValue("false") boolean disableAi) {

  /**
   * Compact canonical constructor — the ONLY constructor, deliberately.
   *
   * <p>This record used to carry a no-argument constructor as well. With two of them, Spring's
   * constructor binding chose the no-argument one and never read the property: the kill-switch
   * could not be switched on, by yaml, by environment variable or by system property. It read
   * "inactive" in the startup log of every deployment this platform has ever had, and the log line
   * was correct — which is why nobody noticed (ADR-055 §8).
   *
   * <p>{@code @DefaultValue} supplies the default now, which is what the removed constructor was
   * there for and does not compete for the binder's attention.
   */
  public SecurityProperties {}
}
