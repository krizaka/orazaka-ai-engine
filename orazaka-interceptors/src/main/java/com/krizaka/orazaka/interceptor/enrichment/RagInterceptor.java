package com.krizaka.orazaka.interceptor.enrichment;

import com.krizaka.orazaka.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.orazaka.core.domain.model.chat.EngineChatOptions;
import com.krizaka.orazaka.core.domain.model.chat.EngineMessage;
import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import com.krizaka.orazaka.core.domain.ports.outbound.KnowledgeService;
import com.krizaka.orazaka.core.infrastructure.config.CoreProperties;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * RAG (Retrieval-Augmented Generation) interceptor — enriches the prompt with relevant knowledge
 * base context retrieved via {@link KnowledgeService}.
 *
 * <p>Conditional on {@code orazaka.core.rag.enabled} being {@code true}.
 *
 * @see KnowledgeService#retrieveContext(String, int)
 */
class RagInterceptor implements PromptContextInterceptor {
  private final CoreProperties properties;
  private final KnowledgeService knowledgeService;

  RagInterceptor(CoreProperties properties, KnowledgeService knowledgeService) {
    this.properties = Objects.requireNonNull(properties, "CoreProperties must not be null");
    this.knowledgeService =
        Objects.requireNonNull(knowledgeService, "KnowledgeService must not be null");
  }

  @Override
  public EngineChatOptions preProcess(
      InternalChatRequest request,
      String promptText,
      List<EngineMessage> messages,
      EngineChatOptions options) {
    Optional.ofNullable(properties.rag())
        .filter(CoreProperties.RagConfig::enabled)
        .ifPresent(
            rag ->
                // A document is content someone wrote, not an instruction: an uploaded file can
                // carry text aimed at the model, so it goes in as data (orazaka-ai-engine#57).
                ExternalContent.insert(
                    messages,
                    "knowledge sources",
                    knowledgeService.retrieveContext(promptText, rag.topK())));
    return options;
  }
}
