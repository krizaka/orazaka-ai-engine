package com.orazaka.interceptor.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orazaka.core.domain.model.chat.InternalChatRequest;
import com.orazaka.core.domain.ports.outbound.McpOrchestrator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;

class McpInterceptorTest {

  @Test
  @DisplayName("preProcess enriches system message with MCP context if available")
  void preProcessWithMcpContext() {
    McpOrchestrator orchestrator = mock(McpOrchestrator.class);
    when(orchestrator.resolveExternalContext()).thenReturn("External MCP Data");

    McpInterceptor interceptor = new McpInterceptor(orchestrator);
    InternalChatRequest request = mock(InternalChatRequest.class);
    List<Message> messages = new ArrayList<>();
    ChatOptions options = mock(ChatOptions.class);

    interceptor.preProcess(request, "prompt", messages, options);

    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).getText()).isEqualTo("MCP Context: External MCP Data");
  }

  @Test
  @DisplayName("preProcess does not enrich messages if MCP context is null or blank")
  void preProcessWithEmptyMcpContext() {
    McpOrchestrator orchestrator = mock(McpOrchestrator.class);
    when(orchestrator.resolveExternalContext()).thenReturn(" ");

    McpInterceptor interceptor = new McpInterceptor(orchestrator);
    InternalChatRequest request = mock(InternalChatRequest.class);
    List<Message> messages = new ArrayList<>();
    ChatOptions options = mock(ChatOptions.class);

    interceptor.preProcess(request, "prompt", messages, options);

    assertThat(messages).isEmpty();
  }
}
