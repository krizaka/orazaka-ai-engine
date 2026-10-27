package com.krizaka.orazaka.adapter.springai.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.krizaka.orazaka.core.domain.model.structured.StructuredOutputRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiStructuredOutputClientTest {

  record Verdict(String label, int score) {}

  private static ChatResponse reply(String text) {
    return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
  }

  @Test
  void theAnswerIsReturnedTypedAndTheFormatIsAskedFor() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(reply("{\"label\":\"ok\",\"score\":7}"));
    var client = new SpringAiStructuredOutputClient(model);

    Verdict verdict =
        client.generate(new StructuredOutputRequest<>("Judge.", "Is it fine?", Verdict.class));

    assertThat(verdict).isEqualTo(new Verdict("ok", 7));
    var prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(model).call(prompt.capture());
    assertThat(prompt.getValue().getContents()).contains("Judge.").contains("Is it fine?");
    assertThat(prompt.getValue().getInstructions().getFirst().getText()).contains("score");
  }

  @Test
  void anAnswerOfTheWrongShapeIsRefused() {
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(reply("not json at all"));
    var client = new SpringAiStructuredOutputClient(model);

    assertThatThrownBy(
            () -> client.generate(new StructuredOutputRequest<>("", "Is it fine?", Verdict.class)))
        .isInstanceOf(IllegalStateException.class);
  }
}
