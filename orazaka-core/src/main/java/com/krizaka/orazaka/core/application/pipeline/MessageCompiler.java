package com.krizaka.orazaka.core.application.pipeline;

import com.krizaka.orazaka.core.domain.model.chat.EngineChatOptions;
import com.krizaka.orazaka.core.domain.model.chat.EngineMessage;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import com.krizaka.orazaka.core.infrastructure.support.CoreException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

/**
 * Compiles Spring AI {@link Message} lists from {@link InternalChatRequest} history and the current
 * prompt text. Handles media attachment embedding for vision-capable models.
 *
 * <p>Extracted from {@link EnginePipelineBridge} to keep the bridge focused on orchestration.
 */
final class MessageCompiler {

  private MessageCompiler() {}

  /**
   * Compiles the message history and current prompt into a list of Spring AI messages.
   *
   * @param request The incoming chat request.
   * @param promptText The resolved prompt text (after pipeline refinement).
   * @param mediaResult The extracted media result from the raw query.
   * @param hasMedia Whether the raw query contained embedded media.
   * @return Ordered list of Spring AI messages ready for prompt assembly.
   */
  static List<Message> compile(
      InternalChatRequest request,
      String promptText,
      Base64MediaExtractor.ExtractionResult mediaResult,
      boolean hasMedia) {

    List<Message> messages = new ArrayList<>();
    if (request.messages() != null) {
      for (InternalChatRequest.ChatMessage msg : request.messages()) {
        messages.add(mapMessage(msg));
      }
    }

    if (promptText == null || promptText.isBlank()) {
      return messages;
    }

    if (hasMedia) {
      appendMediaMessage(messages, promptText, mediaResult);
    } else {
      // Text, and only text. Media reaches the model only from the user's own prompt, where the
      // engine counted it before the pipeline ran. Lifting a marker out of the REFINED prompt — as
      // this method used to — attached an image that no guard had been told about: a refiner that
      // "repaired" a malformed marker would have put media in front of the model after every
      // control had passed the turn as text (ADR-063).
      messages.add(new UserMessage(promptText));
    }
    return messages;
  }

  /**
   * Maps an Orazaka chat message to a Spring AI {@link Message} based on the role string.
   *
   * @param msg The Orazaka chat message containing role and content.
   * @return The corresponding Spring AI message ({@link SystemMessage}, {@link AssistantMessage},
   *     or {@link UserMessage}).
   */
  static Message mapMessage(InternalChatRequest.ChatMessage msg) {
    return switch (msg.role().toLowerCase()) {
      case "system" -> new SystemMessage(msg.content());
      case "assistant" -> new AssistantMessage(msg.content());
      default -> new UserMessage(msg.content());
    };
  }

  /**
   * The engine view of library messages. Each keeps the message it came from, so converting back
   * loses nothing (media, tool answers).
   */
  static List<EngineMessage> toEngine(List<Message> messages) {
    List<EngineMessage> engine = new ArrayList<>(messages.size());
    for (Message message : messages) {
      String type = message.getMessageType().getValue();
      EngineMessage.Role role = EngineMessage.Role.USER;
      if ("system".equals(type)) {
        role = EngineMessage.Role.SYSTEM;
      } else if ("assistant".equals(type)) {
        role = EngineMessage.Role.ASSISTANT;
      } else if ("tool".equals(type)) {
        role = EngineMessage.Role.TOOL;
      }
      engine.add(new EngineMessage(role, message.getText(), message));
    }
    return engine;
  }

  /** The library messages of the engine view: the original where there is one, else built. */
  static List<Message> toSpring(List<EngineMessage> messages) {
    List<Message> spring = new ArrayList<>(messages.size());
    for (EngineMessage message : messages) {
      if (message.nativeMessage() instanceof Message original) {
        spring.add(original);
      } else {
        spring.add(build(message));
      }
    }
    return spring;
  }

  private static Message build(EngineMessage message) {
    if (message.role() == EngineMessage.Role.SYSTEM) {
      return new SystemMessage(message.text());
    }
    if (message.role() == EngineMessage.Role.ASSISTANT) {
      return new AssistantMessage(message.text());
    }
    if (message.role() == EngineMessage.Role.TOOL) {
      return org.springframework.ai.chat.messages.ToolResponseMessage.builder()
          .responses(
              List.of(
                  new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                      "", "", message.text())))
          .build();
    }
    return new UserMessage(message.text());
  }

  /** The engine view of library options, which it keeps for the way back. */
  static EngineChatOptions toEngine(ChatOptions options) {
    return options == null
        ? null
        : new EngineChatOptions(
            options.getModel(), options.getTemperature(), options.getMaxTokens(), options);
  }

  /**
   * The library options of the engine view. Settings left as they were give the original back; a
   * model, temperature or token cap the view sets differently is applied to a copy of the original
   * (a {@code null} setting means "as the original"), so the provider's own settings (tools,
   * context) survive and the options the caller was handed are not mutated; with no original the
   * options are built from the settings.
   */
  static ChatOptions toSpring(EngineChatOptions options) {
    if (options == null) {
      return null;
    }
    if (options.nativeOptions() instanceof ChatOptions original) {
      return withEdits(original, options);
    }
    return ChatOptions.builder()
        .model(options.model())
        .temperature(options.temperature())
        .maxTokens(options.maxTokens())
        .build();
  }

  /** The original, or a copy with the settings the engine view sets and the original does not. */
  private static ChatOptions withEdits(ChatOptions original, EngineChatOptions options) {
    boolean model = differs(options.model(), original.getModel());
    boolean temperature = differs(options.temperature(), original.getTemperature());
    boolean maxTokens = differs(options.maxTokens(), original.getMaxTokens());
    if (!model && !temperature && !maxTokens) {
      return original;
    }
    var copy = original.mutate();
    if (model) {
      copy.model(options.model());
    }
    if (temperature) {
      copy.temperature(options.temperature());
    }
    if (maxTokens) {
      copy.maxTokens(options.maxTokens());
    }
    return copy.build();
  }

  /** A setting the engine view carries (not {@code null}) that is not the original's. */
  private static boolean differs(Object edited, Object original) {
    return edited != null && !edited.equals(original);
  }

  /** Appends a user message with an image media attachment. */
  private static void appendMediaMessage(
      List<Message> messages,
      String promptText,
      Base64MediaExtractor.ExtractionResult mediaResult) {
    byte[] imageBytes =
        mediaResult
            .imageBytes()
            .orElseThrow(() -> new CoreException("Expected media bytes but none found"));
    Media media = new Media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(imageBytes));
    messages.add(UserMessage.builder().text(promptText).media(media).build());
  }
}
