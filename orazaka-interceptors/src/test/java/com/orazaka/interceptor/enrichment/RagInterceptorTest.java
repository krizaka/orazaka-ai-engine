package com.orazaka.interceptor.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.orazaka.core.domain.model.chat.InternalChatRequest;
import com.orazaka.core.domain.ports.outbound.KnowledgeService;
import com.orazaka.core.infrastructure.config.CoreProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;

class RagInterceptorTest {

  @Test
  @DisplayName("preProcess injects RAG context if enabled and context is not empty")
  void preProcessInjectsRagContext() {
    CoreProperties props = mock(CoreProperties.class);
    CoreProperties.RagConfig ragConfig = mock(CoreProperties.RagConfig.class);
    when(props.rag()).thenReturn(ragConfig);
    when(ragConfig.enabled()).thenReturn(true);
    when(ragConfig.topK()).thenReturn(5);

    KnowledgeService knowledgeService = mock(KnowledgeService.class);
    when(knowledgeService.retrieveContext("test query", 5)).thenReturn("Relevant knowledge chunks");

    RagInterceptor interceptor = new RagInterceptor(props, knowledgeService);
    InternalChatRequest request = mock(InternalChatRequest.class);
    List<Message> messages = new ArrayList<>();
    ChatOptions options = mock(ChatOptions.class);

    interceptor.preProcess(request, "test query", messages, options);

    assertThat(messages).hasSize(1);
    assertThat(messages.get(0).getText()).contains("RAG Context:", "Relevant knowledge chunks");
  }

  @Test
  @DisplayName("preProcess does nothing if RAG config is disabled")
  void preProcessRagDisabled() {
    CoreProperties props = mock(CoreProperties.class);
    CoreProperties.RagConfig ragConfig = mock(CoreProperties.RagConfig.class);
    when(props.rag()).thenReturn(ragConfig);
    when(ragConfig.enabled()).thenReturn(false);

    KnowledgeService knowledgeService = mock(KnowledgeService.class);

    RagInterceptor interceptor = new RagInterceptor(props, knowledgeService);
    InternalChatRequest request = mock(InternalChatRequest.class);
    List<Message> messages = new ArrayList<>();
    ChatOptions options = mock(ChatOptions.class);

    interceptor.preProcess(request, "test query", messages, options);

    assertThat(messages).isEmpty();
    verifyNoInteractions(knowledgeService);
  }
}
