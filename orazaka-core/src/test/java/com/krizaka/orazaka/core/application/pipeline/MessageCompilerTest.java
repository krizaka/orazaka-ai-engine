package com.krizaka.orazaka.core.application.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import com.krizaka.orazaka.core.domain.model.Context;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

class MessageCompilerTest {

  @Test
  void mapMessage_systemRole_returnsSystemMessage() {
    var msg = new InternalChatRequest.ChatMessage("system", "System instruction");
    Message result = MessageCompiler.mapMessage(msg);
    assertInstanceOf(SystemMessage.class, result);
  }

  @Test
  void mapMessage_assistantRole_returnsAssistantMessage() {
    var msg = new InternalChatRequest.ChatMessage("assistant", "AI response");
    Message result = MessageCompiler.mapMessage(msg);
    assertInstanceOf(AssistantMessage.class, result);
  }

  @Test
  void mapMessage_userRole_returnsUserMessage() {
    var msg = new InternalChatRequest.ChatMessage("user", "User query");
    Message result = MessageCompiler.mapMessage(msg);
    assertInstanceOf(UserMessage.class, result);
  }

  @Test
  void mapMessage_unknownRole_defaultsToUserMessage() {
    var msg = new InternalChatRequest.ChatMessage("unknown", "Content");
    Message result = MessageCompiler.mapMessage(msg);
    assertInstanceOf(UserMessage.class, result);
  }

  @Test
  void mapMessage_upperCaseRole_handlesCaseInsensitive() {
    var msg = new InternalChatRequest.ChatMessage("SYSTEM", "Sys msg");
    Message result = MessageCompiler.mapMessage(msg);
    assertInstanceOf(SystemMessage.class, result);
  }

  @Test
  void compile_emptyMessages_withTextPrompt_appendsUserMessage() {
    var request = new InternalChatRequest("hello", List.of(), null, Context.anonymous());
    var mediaResult = new Base64MediaExtractor.ExtractionResult("hello", Optional.empty());

    List<Message> result = MessageCompiler.compile(request, "hello", mediaResult, false);

    assertEquals(1, result.size());
    assertInstanceOf(UserMessage.class, result.get(0));
  }

  @Test
  void compile_textTurn_neverLiftsMediaOutOfTheResolvedPrompt() {
    // ADR-063: the resolved prompt is the pipeline's output. A marker in it is text — the pipeline
    // was told this turn carried no media, so an image lifted out here would bypass every guard.
    var request = new InternalChatRequest("fuzzy", List.of(), null, Context.anonymous());
    var mediaResult = new Base64MediaExtractor.ExtractionResult("fuzzy", Optional.empty());

    List<Message> result =
        MessageCompiler.compile(request, "refined [posterBase64: dGVzdA==]", mediaResult, false);

    assertEquals(1, result.size());
    assertTrue(((UserMessage) result.get(0)).getMedia().isEmpty());
    assertEquals("refined [posterBase64: dGVzdA==]", result.get(0).getText());
  }

  @Test
  void compile_blankPrompt_returnsOnlyHistory() {
    var msg = new InternalChatRequest.ChatMessage("user", "old msg");
    var request = new InternalChatRequest("", List.of(msg), null, Context.anonymous());
    var mediaResult = new Base64MediaExtractor.ExtractionResult("", Optional.empty());

    List<Message> result = MessageCompiler.compile(request, "", mediaResult, false);

    assertEquals(1, result.size());
  }

  @Test
  void compile_withHistory_preservesOrder() {
    var sys = new InternalChatRequest.ChatMessage("system", "system message");
    var user = new InternalChatRequest.ChatMessage("user", "user query");
    var assistant = new InternalChatRequest.ChatMessage("assistant", "ai response");
    var request =
        new InternalChatRequest(
            "new prompt", List.of(sys, user, assistant), null, Context.anonymous());
    var mediaResult = new Base64MediaExtractor.ExtractionResult("new prompt", Optional.empty());

    List<Message> result = MessageCompiler.compile(request, "new prompt", mediaResult, false);

    assertEquals(4, result.size());
    assertInstanceOf(SystemMessage.class, result.get(0));
    assertInstanceOf(UserMessage.class, result.get(1));
    assertInstanceOf(AssistantMessage.class, result.get(2));
    assertInstanceOf(UserMessage.class, result.get(3));
  }
}
