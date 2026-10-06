package com.orazaka.interceptor.tooling;

import com.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.orazaka.core.domain.model.chat.InternalChatRequest;
import com.orazaka.core.domain.ports.outbound.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Tool execution interceptor — analyzes prompt content to selectively attach relevant {@link
 * ToolCallback} definitions to the chat options.
 *
 * <p>Uses keyword-based demand detection to determine which tools are relevant for the current
 * prompt (e.g., image analysis for "poster", audio analysis for "audio").
 *
 * @see ToolRegistry
 */
class ToolInterceptor implements PromptContextInterceptor {
  private final ToolRegistry toolRegistry;

  ToolInterceptor(ToolRegistry toolRegistry) {
    this.toolRegistry = toolRegistry;
  }

  @Override
  public ChatOptions preProcess(
      InternalChatRequest request, String promptText, List<Message> messages, ChatOptions options) {
    if (request.streaming()) {
      return options;
    }
    if (options != null && options.getModel() != null) {
      String modelLower = options.getModel().toLowerCase();
      if (modelLower.contains("vision")
          || modelLower.contains("llava")
          || modelLower.contains("bakllava")) {
        return options;
      }
    }
    if (toolRegistry == null || toolRegistry.getRegisteredTools().isEmpty()) return options;

    List<ToolCallback> demandedTools = collectDemandedTools(request, promptText);
    if (demandedTools.isEmpty()) return options;

    return attachTools(options, demandedTools);
  }

  private List<ToolCallback> collectDemandedTools(InternalChatRequest request, String promptText) {
    String lowerContext = extractFullText(request, promptText).toLowerCase();
    List<ToolCallback> demanded = new ArrayList<>();

    for (ToolCallback tool : toolRegistry.getRegisteredTools()) {
      String name = tool.getToolDefinition().name();
      boolean isSpecialized = isSpecializedTool(name);
      if (!isSpecialized || matchesDemand(name, lowerContext)) {
        demanded.add(tool);
      }
    }
    return demanded;
  }

  private boolean isSpecializedTool(String name) {
    return "analyzePoster".equals(name) || "analyzeAudioExtract".equals(name);
  }

  private boolean matchesDemand(String toolName, String lowerContext) {
    return ("analyzePoster".equals(toolName) && demandsPosterTool(lowerContext))
        || ("analyzeAudioExtract".equals(toolName) && demandsAudioTool(lowerContext));
  }

  private String extractFullText(InternalChatRequest request, String promptText) {
    StringBuilder text = new StringBuilder(promptText != null ? promptText : "");
    text.append(" ").append(request.prompt() != null ? request.prompt() : "");
    if (request.messages() != null) {
      request.messages().stream()
          .filter(m -> m.content() != null)
          .forEach(m -> text.append(" ").append(m.content()));
    }
    return text.toString();
  }

  private boolean demandsPosterTool(String lowerContext) {
    return lowerContext.contains("poster")
        || lowerContext.contains("image")
        || lowerContext.contains("visual")
        || lowerContext.contains("picture");
  }

  private boolean demandsAudioTool(String lowerContext) {
    return lowerContext.contains("audio")
        || lowerContext.contains("clip")
        || lowerContext.contains("voice")
        || lowerContext.contains("music")
        || lowerContext.contains("sound");
  }

  private ChatOptions attachTools(ChatOptions options, List<ToolCallback> demandedTools) {
    // Spring AI 2.0 dropped builder .toolNames(Set) — tool registration is via .toolCallbacks()
    // alone.
    return switch (options) {
      case null -> options;
      case OllamaChatOptions ollama ->
          OllamaChatOptions.builder()
              .model(ollama.getModel())
              .temperature(ollama.getTemperature())
              .numPredict(ollama.getNumPredict())
              .numCtx(8192)
              .toolCallbacks(demandedTools)
              .build();
      case OpenAiChatOptions openai ->
          OpenAiChatOptions.builder()
              .model(openai.getModel())
              .temperature(openai.getTemperature())
              .maxTokens(openai.getMaxTokens())
              .toolCallbacks(demandedTools)
              .build();
      default -> options;
    };
  }
}
