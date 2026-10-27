package com.krizaka.orazaka.adapter.springai.chat;

import com.krizaka.orazaka.adapter.springai.prompt.SpringPreparedPrompt;
import com.krizaka.orazaka.core.application.routing.DynamicChatModelFactory;
import com.krizaka.orazaka.core.domain.model.chat.ChatCompletion;
import com.krizaka.orazaka.core.domain.model.chat.ChatRoute;
import com.krizaka.orazaka.core.domain.model.chat.PreparedPrompt;
import com.krizaka.orazaka.core.domain.model.chat.TokenUsage;
import com.krizaka.orazaka.core.domain.ports.outbound.ChatInvocation;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * {@link ChatInvocation} on Spring AI: the platform's {@link ChatModel}, or a model built for the
 * user's own key. The only place the engines' calls meet the library's model, prompt and response.
 */
public class SpringAiChatInvocation implements ChatInvocation {

  private final ChatModel platformModel;
  private final DynamicChatModelFactory<ChatModel> modelFactory;

  /**
   * Creates the invocation.
   *
   * @param platformModel the platform's own model
   * @param modelFactory builds a model for a user's key
   */
  public SpringAiChatInvocation(
      ChatModel platformModel, DynamicChatModelFactory<ChatModel> modelFactory) {
    this.platformModel = Objects.requireNonNull(platformModel, "ChatModel must not be null");
    this.modelFactory =
        Objects.requireNonNull(modelFactory, "DynamicChatModelFactory must not be null");
  }

  @Override
  public ChatCompletion call(PreparedPrompt prompt, ChatRoute route) {
    return completion(modelOf(prompt, route).call(promptOf(prompt)), false);
  }

  @Override
  public Flux<ChatCompletion> stream(PreparedPrompt prompt, ChatRoute route) {
    return modelOf(prompt, route).stream(promptOf(prompt)).map(r -> completion(r, true));
  }

  private ChatModel modelOf(PreparedPrompt prompt, ChatRoute route) {
    if (route == null || route.isPlatform()) {
      return platformModel;
    }
    return modelFactory.createChatModel(route.provider(), prompt.modelName(), route.apiKey());
  }

  private static Prompt promptOf(PreparedPrompt prompt) {
    if (prompt instanceof SpringPreparedPrompt spring) {
      return spring.prompt();
    }
    throw new IllegalArgumentException(
        "Not a Spring AI prompt: " + (prompt == null ? null : prompt.getClass().getName()));
  }

  /** Reads text and usage; a chunk without text is empty rather than absent when streaming. */
  private static ChatCompletion completion(ChatResponse response, boolean emptyIfAbsent) {
    String text = null;
    if (response != null
        && response.getResult() != null
        && response.getResult().getOutput() != null) {
      text = response.getResult().getOutput().getText();
    }
    if (text == null && emptyIfAbsent) {
      text = "";
    }
    TokenUsage usage = TokenUsage.none();
    if (response != null && response.getMetadata() != null) {
      var reported = response.getMetadata().getUsage();
      if (reported != null) {
        usage =
            TokenUsage.reported(
                reported.getPromptTokens(),
                reported.getCompletionTokens(),
                reported.getTotalTokens());
      }
    }
    return new ChatCompletion(text, usage);
  }
}
