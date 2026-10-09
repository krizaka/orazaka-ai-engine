package com.krizaka.orazaka.interceptor.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.krizaka.orazaka.core.domain.model.Context;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import com.krizaka.orazaka.core.domain.ports.outbound.MemoryResolver;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;

class MemoryInterceptorTest {

  @Test
  @DisplayName("preProcess injects memory history before current prompt")
  void preProcessInjectsHistory() {
    MemoryResolver resolver = mock(MemoryResolver.class);
    ChatMemory chatMemory = mock(ChatMemory.class);
    when(resolver.resolve("conv-123")).thenReturn(chatMemory);

    List<Message> history = List.of(new UserMessage("Hi"), new AssistantMessage("Hello"));
    when(chatMemory.get("conv-123")).thenReturn(history);

    MemoryInterceptor interceptor = new MemoryInterceptor(resolver);

    InternalChatRequest request = mock(InternalChatRequest.class);
    Context context = mock(Context.class);
    when(request.context()).thenReturn(context);
    when(context.conversationId()).thenReturn("conv-123");

    List<Message> messages = new ArrayList<>(List.of(new UserMessage("Current query")));
    ChatOptions options = mock(ChatOptions.class);

    interceptor.preProcess(request, "Current query", messages, options);

    assertThat(messages).hasSize(3);
    assertThat(messages.get(0).getText()).isEqualTo("Hi");
    assertThat(messages.get(1).getText()).isEqualTo("Hello");
    assertThat(messages.get(2).getText()).isEqualTo("Current query");
  }

  @Test
  @DisplayName("postProcess appends query and response to ChatMemory")
  void postProcessAppendsToMemory() {
    MemoryResolver resolver = mock(MemoryResolver.class);
    ChatMemory chatMemory = mock(ChatMemory.class);
    when(resolver.resolve("conv-123")).thenReturn(chatMemory);

    MemoryInterceptor interceptor = new MemoryInterceptor(resolver);

    InternalChatRequest request = mock(InternalChatRequest.class);
    Context context = mock(Context.class);
    when(request.context()).thenReturn(context);
    when(context.conversationId()).thenReturn("conv-123");

    interceptor.postProcess(request, "User Prompt", "AI Response");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
    verify(chatMemory).add(eq("conv-123"), captor.capture());

    List<Message> added = captor.getValue();
    assertThat(added).hasSize(2);
    assertThat(added.get(0)).isInstanceOf(UserMessage.class);
    assertThat(added.get(0).getText()).isEqualTo("User Prompt");
    assertThat(added.get(1)).isInstanceOf(AssistantMessage.class);
    assertThat(added.get(1).getText()).isEqualTo("AI Response");
  }
}
