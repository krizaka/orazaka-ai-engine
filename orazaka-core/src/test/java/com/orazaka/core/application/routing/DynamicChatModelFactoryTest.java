package com.orazaka.core.application.routing;

import static com.orazaka.test.TestConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orazaka.core.infrastructure.provider.DynamicChatModelProvider;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class DynamicChatModelFactoryTest {

  @Test
  @DisplayName("createChatModel throws if model name is blank")
  void modelNameValidation() {
    DynamicChatModelFactory factory = new DynamicChatModelFactory(List.of());
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.createChatModel(PROVIDER_OPENAI, null, "key"));
    assertThrows(
        IllegalArgumentException.class, () -> factory.createChatModel(PROVIDER_OPENAI, "", "key"));
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.createChatModel(PROVIDER_OPENAI, "  ", "key"));
  }

  @Test
  @DisplayName("createChatModel delegates to supporting provider")
  void delegatesToSupportingProvider() {
    DynamicChatModelProvider provider1 = mock(DynamicChatModelProvider.class);
    DynamicChatModelProvider provider2 = mock(DynamicChatModelProvider.class);

    when(provider1.supports(PROVIDER_OPENAI)).thenReturn(false);
    when(provider2.supports(PROVIDER_OPENAI)).thenReturn(true);

    ChatModel mockModel = mock(ChatModel.class);
    when(provider2.create("gpt-4", "test-key")).thenReturn(mockModel);

    DynamicChatModelFactory factory = new DynamicChatModelFactory(List.of(provider1, provider2));
    ChatModel chatModel = factory.createChatModel(PROVIDER_OPENAI, "gpt-4", "test-key");

    assertEquals(mockModel, chatModel);
  }

  @Test
  @DisplayName("createChatModel throws if no supporting provider is found")
  void unsupportedProvider() {
    DynamicChatModelProvider provider = mock(DynamicChatModelProvider.class);
    when(provider.supports("anthropic")).thenReturn(false);

    DynamicChatModelFactory factory = new DynamicChatModelFactory(List.of(provider));
    assertThrows(
        IllegalArgumentException.class,
        () -> factory.createChatModel("anthropic", "claude-3", "key"));
  }
}
