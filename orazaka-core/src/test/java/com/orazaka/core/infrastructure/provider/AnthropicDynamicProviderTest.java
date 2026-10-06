package com.orazaka.core.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class AnthropicDynamicProviderTest {

  @Test
  void testSupports() {
    AnthropicDynamicProvider provider = new AnthropicDynamicProvider();
    assertThat(provider.supports("claude")).isTrue();
    assertThat(provider.supports("anthropic")).isTrue();
    assertThat(provider.supports("CLAUDE")).isTrue();
    assertThat(provider.supports("other")).isFalse();
  }

  @Test
  void testCreateValid() {
    AnthropicDynamicProvider provider = new AnthropicDynamicProvider();
    ChatModel model = provider.create("claude-3-5-sonnet", "my-api-key");
    assertThat(model).isNotNull();
  }

  @Test
  void testCreateInvalid() {
    AnthropicDynamicProvider provider = new AnthropicDynamicProvider();
    assertThatThrownBy(() -> provider.create(null, "my-api-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Model name is required for Anthropic dynamic provider");

    assertThatThrownBy(() -> provider.create("   ", "my-api-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Model name is required for Anthropic dynamic provider");
  }
}
