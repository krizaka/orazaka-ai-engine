package com.krizaka.orazaka.interceptor.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.krizaka.orazaka.core.domain.model.chat.EngineChatOptions;
import com.krizaka.orazaka.core.domain.model.chat.EngineMessage;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import com.krizaka.orazaka.core.domain.ports.outbound.KnowledgeService;
import com.krizaka.orazaka.core.infrastructure.config.CoreProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    List<EngineMessage> messages = new ArrayList<>();
    EngineChatOptions options = new EngineChatOptions(null, null, null);

    interceptor.preProcess(request, "test query", messages, options);

    // The chunks are data in a user-role block, never a system instruction (#57).
    assertThat(messages)
        .filteredOn(m -> m.role() == EngineMessage.Role.SYSTEM)
        .noneMatch(m -> m.text().contains("Relevant knowledge chunks"));
    assertThat(messages)
        .filteredOn(m -> m.role() == EngineMessage.Role.USER)
        .anyMatch(
            m ->
                m.text().startsWith(ExternalContent.OPEN)
                    && m.text().contains("Relevant knowledge chunks"));
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
    List<EngineMessage> messages = new ArrayList<>();
    EngineChatOptions options = new EngineChatOptions(null, null, null);

    interceptor.preProcess(request, "test query", messages, options);

    assertThat(messages).isEmpty();
    verifyNoInteractions(knowledgeService);
  }
}
