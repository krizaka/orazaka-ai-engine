package com.orazaka.core.application.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.orazaka.core.domain.model.PromptContext;
import com.orazaka.core.domain.model.chat.InternalChatRequest;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.prompt.ChatOptions;

class PromptContextInterceptorTest {

  @Test
  @DisplayName("default interface methods behave correctly")
  void defaultInterfaceMethods() {
    PromptContextInterceptor interceptor = new PromptContextInterceptor() {};
    // getId() defaults to simple class name
    assertNotNull(interceptor.getId());
    assertEquals(false, interceptor.isAiDependent());

    PromptContext context = new PromptContext("test query", Map.of());
    assertSame(context, interceptor.beforeExecution(context));

    InternalChatRequest request = Mockito.mock(InternalChatRequest.class);
    ChatOptions options = Mockito.mock(ChatOptions.class);
    assertSame(options, interceptor.preProcess(request, "prompt", new ArrayList<>(), options));

    // Calling postProcess should not throw any exception
    interceptor.postProcess(request, "prompt", "response");
  }
}
