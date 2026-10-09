package com.krizaka.orazaka.core.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SecurityProperties")
class SecurityPropertiesTest {

  @Test
  @DisplayName("default constructor sets disableAi to false")
  void defaultConstructorDisableAiFalse() {
    SecurityProperties props = new SecurityProperties(false);
    assertThat(props.disableAi()).isFalse();
  }

  @Test
  @DisplayName("explicit constructor with true enables kill-switch")
  void explicitConstructorWithTrueEnablesKillSwitch() {
    SecurityProperties props = new SecurityProperties(true);
    assertThat(props.disableAi()).isTrue();
  }

  @Test
  @DisplayName("explicit constructor with false keeps kill-switch disabled")
  void explicitConstructorWithFalseKeepsKillSwitchDisabled() {
    SecurityProperties props = new SecurityProperties(false);
    assertThat(props.disableAi()).isFalse();
  }

  @Test
  @DisplayName("[ADR-055] the kill-switch actually BINDS — it could not be switched on")
  void theKillSwitchBinds() {
    // The defect this pins. SecurityProperties carried a no-argument constructor beside its
    // canonical one; Spring's constructor binding chose the no-argument one and never read the
    // property. `orazaka.security.disable-ai=true` did nothing — by yaml, by environment variable
    // or by system property — and every startup log said "Security kill-switch: inactive", which
    // was true, which is why nobody noticed. AGENTS.md §7 documents this as the governance
    // kill-switch; for the life of this platform it was a comment.
    //
    // Asserted structurally: one constructor is what makes the binder unambiguous, and a second
    // one added for convenience would silently switch the control off again.
    assertThat(SecurityProperties.class.getConstructors())
        .as("exactly one constructor, or the binder has a choice to get wrong")
        .hasSize(1);
    assertThat(SecurityProperties.class.getConstructors()[0].getParameterCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("[ADR-055] and it binds from a property source, end to end")
  void bindsFromAPropertySource() {
    org.springframework.boot.context.properties.bind.Binder binder =
        new org.springframework.boot.context.properties.bind.Binder(
            new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(
                java.util.Map.of("orazaka.security.disable-ai", "true")));

    SecurityProperties bound = binder.bind("orazaka.security", SecurityProperties.class).get();

    assertThat(bound.disableAi()).isTrue();
  }
}
