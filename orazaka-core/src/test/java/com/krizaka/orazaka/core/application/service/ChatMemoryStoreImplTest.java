package com.krizaka.orazaka.core.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.krizaka.orazaka.persistence.domain.model.ChatMessageDto;
import com.krizaka.orazaka.persistence.domain.ports.inbound.ChatMemoryPersistenceProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.dao.QueryTimeoutException;

class ChatMemoryStoreImplTest {

  private final ChatMemoryPersistenceProvider provider = mock(ChatMemoryPersistenceProvider.class);
  private final ChatMemoryStoreImpl store = new ChatMemoryStoreImpl(provider);
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final Logger logger = (Logger) LoggerFactory.getLogger(ChatMemoryStoreImpl.class);

  @BeforeEach
  void attach() {
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void detach() {
    logger.detachAppender(logs);
  }

  /** Writes the messages, then reads back what the store persisted. */
  @SuppressWarnings("unchecked")
  private List<Message> roundTrip(List<Message> messages) {
    store.append("c-1", messages);
    ArgumentCaptor<List<ChatMessageDto>> written = ArgumentCaptor.forClass(List.class);
    verify(provider).appendAll(written.capture());
    when(provider.findRecent(anyString(), anyInt())).thenReturn(written.getValue());
    return store.findRecent("c-1", 10);
  }

  @Test
  @DisplayName("[#27] a tool answer is stored as a tool and read back as a tool, id and name kept")
  void toolResponseRoundTrips() {
    ToolResponseMessage tool =
        ToolResponseMessage.builder()
            .responses(
                List.of(new ToolResponseMessage.ToolResponse("call-1", "weather", "{\"c\":21}")))
            .build();

    List<Message> back = roundTrip(List.of(tool));

    assertThat(back).singleElement().isInstanceOf(ToolResponseMessage.class);
    ToolResponseMessage read = (ToolResponseMessage) back.getFirst();
    assertThat(read.getResponses()).hasSize(1);
    assertThat(read.getResponses().getFirst().id()).isEqualTo("call-1");
    assertThat(read.getResponses().getFirst().name()).isEqualTo("weather");
    assertThat(read.getResponses().getFirst().responseData()).isEqualTo("{\"c\":21}");
  }

  @Test
  @DisplayName("[#27] user, assistant and system messages keep their type")
  void otherTypesRoundTrip() {
    List<Message> back =
        roundTrip(List.of(new SystemMessage("s"), new UserMessage("u"), new AssistantMessage("a")));

    assertThat(back)
        .extracting(Message::getMessageType)
        .containsExactly(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT);
    assertThat(back).extracting(Message::getText).containsExactly("s", "u", "a");
  }

  @Test
  @DisplayName("[#27] a tool row written before answers were structured is read as plain text")
  void legacyToolRow() {
    when(provider.findRecent(anyString(), anyInt()))
        .thenReturn(List.of(new ChatMessageDto("c-1", "tool", "sunny", null)));

    Message read = store.findRecent("c-1", 5).getFirst();

    assertThat(read).isInstanceOf(ToolResponseMessage.class);
    assertThat(((ToolResponseMessage) read).getResponses().getFirst().responseData())
        .isEqualTo("sunny");
  }

  @Test
  @DisplayName(
      "[#27] a persistence failure degrades and logs the stack trace on read, write, clear")
  void failuresKeepTheirStackTrace() {
    QueryTimeoutException failure = new QueryTimeoutException("pool timeout");
    when(provider.findRecent(anyString(), anyInt())).thenThrow(failure);
    doThrow(failure).when(provider).appendAll(anyList());
    doThrow(failure).when(provider).deleteByConversationId(anyString());

    assertThat(store.findRecent("c-1", 5)).isEmpty();
    store.append("c-1", new ArrayList<>(List.of(new UserMessage("hi"))));
    store.clear("c-1");

    assertThat(logs.list).hasSize(3);
    assertThat(logs.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNotNull());
  }
}
