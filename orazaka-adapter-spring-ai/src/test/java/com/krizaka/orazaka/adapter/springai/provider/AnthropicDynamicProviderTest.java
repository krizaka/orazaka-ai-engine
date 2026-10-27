package com.krizaka.orazaka.adapter.springai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class AnthropicDynamicProviderTest {

  @Test
  void supports() {
    AnthropicDynamicProvider provider = new AnthropicDynamicProvider();
    assertThat(provider.supports("claude")).isTrue();
    assertThat(provider.supports("anthropic")).isTrue();
    assertThat(provider.supports("CLAUDE")).isTrue();
    assertThat(provider.supports("other")).isFalse();
  }

  @Test
  void createValid() {
    AnthropicDynamicProvider provider = new AnthropicDynamicProvider();
    ChatModel model = provider.create("claude-3-5-sonnet", "my-api-key");
    assertThat(model).isNotNull();
  }

  @Test
  void createInvalid() {
    AnthropicDynamicProvider provider = new AnthropicDynamicProvider();
    assertThatThrownBy(() -> provider.create(null, "my-api-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Model name is required for Anthropic dynamic provider");

    assertThatThrownBy(() -> provider.create("   ", "my-api-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Model name is required for Anthropic dynamic provider");
  }
}
