package com.krizaka.orazaka.interceptor.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class ValidationAutoConfigurationTest {

  @Test
  @DisplayName(
      "ValidationAutoConfiguration registers CostShieldInterceptor and ClosedLoopValidationInterceptor")
  void registersBeans() {
    ValidationAutoConfiguration config = new ValidationAutoConfiguration();

    CostShieldInterceptor costShield = config.costShieldInterceptor();
    assertThat(costShield).isNotNull();

    ClosedLoopValidationProperties props =
        new ClosedLoopValidationProperties(true, 3, true, true, true, false);
    ChatModel chatModel = mock(ChatModel.class);
    Map<String, ChatModel> chatModels = Map.of("openai", chatModel);

    ClosedLoopValidationInterceptor advisorWithModel =
        config.closedLoopValidationInterceptor(props, chatModels, null, null);
    assertThat(advisorWithModel).isNotNull();

    ClosedLoopValidationInterceptor advisorWithoutModel =
        config.closedLoopValidationInterceptor(props, Collections.emptyMap(), null, null);
    assertThat(advisorWithoutModel).isNotNull();

    SpringAiTestShaperAdapter shaper = config.springAiTestShaperAdapter(chatModels);
    assertThat(shaper).isNotNull();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> config.springAiTestShaperAdapter(Collections.emptyMap()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no ChatModel available");
  }
}
