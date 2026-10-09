package com.orazaka.core.infrastructure.adapter.knowledge;

import com.krizaka.security.token.ServiceTokenProvider;
import com.orazaka.core.domain.ports.outbound.KnowledgeService;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP implementation of the {@link KnowledgeService} port against the knowledge service's internal
 * API (Phase 4 cutover). Every failure degrades to an empty string: RAG enrichment and tool source
 * lookup are additive concerns, so an unreachable knowledge service must never fail the calling
 * request.
 */
@Component
@EnableConfigurationProperties(KnowledgeProperties.class)
class HttpKnowledgeAdapter implements KnowledgeService {

  private static final Logger logger = LoggerFactory.getLogger(HttpKnowledgeAdapter.class);

  private final RestClient knowledgeClient;

  HttpKnowledgeAdapter(KnowledgeProperties properties) {
    ServiceTokenProvider tokens =
        new ServiceTokenProvider(properties.serviceSecret(), "orazaka-core");
    this.knowledgeClient =
        RestClient.builder()
            .baseUrl(properties.baseUrl().toString())
            // /internal/v1/knowledge/** is authenticated as of ADR-035's follow-up. Attached at
            // the client, not per call, so the next retrieval method added cannot forget it.
            .requestInitializer(request -> request.getHeaders().setBearerAuth(tokens.token()))
            .build();
  }

  /** Wire shape of the internal knowledge responses (contract copy — no shared jar). */
  record ContentResponse(String content) {}

  @Override
  public String retrieveContext(String query, int topK) {
    Map<String, Object> request = new HashMap<>();
    request.put("query", query);
    request.put("topK", topK);
    return post("/internal/v1/knowledge/retrieve", request);
  }

  @Override
  public String searchSources(String toolId, String userId, String query) {
    Map<String, Object> request = new HashMap<>();
    request.put("toolId", toolId);
    request.put("userId", userId);
    request.put("query", query);
    return post("/internal/v1/knowledge/sources/search", request);
  }

  private String post(String path, Map<String, Object> request) {
    try {
      ContentResponse response =
          knowledgeClient
              .post()
              .uri(path)
              .header("Content-Type", "application/json")
              .body(request)
              .retrieve()
              .body(ContentResponse.class);
      return response != null && response.content() != null ? response.content() : "";
    } catch (Exception e) {
      logger.warn(
          "Knowledge service call {} failed — degrading to empty content: {}",
          path,
          e.getMessage());
      return "";
    }
  }
}
