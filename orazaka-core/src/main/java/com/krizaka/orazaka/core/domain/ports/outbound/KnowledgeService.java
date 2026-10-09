package com.krizaka.orazaka.core.domain.ports.outbound;

/**
 * Outbound port to the knowledge context (RAG retrieval + tool source lookup), owned by the
 * knowledge service since Phase 4. Implementations degrade explicitly: when the knowledge service
 * is unreachable or holds no matching content, they return an empty string — the pipeline enriches
 * with nothing instead of failing the request.
 */
public interface KnowledgeService {

  /**
   * Retrieves relevant context for a given query from the vector store.
   *
   * @param query the user query
   * @param topK number of relevant documents to retrieve
   * @return formatted context, or an empty string when nothing is available
   */
  String retrieveContext(String query, int topK);

  /**
   * Searches the registered tool RAG sources by substring, scoped to a tool and requesting user
   * (platform-wide sources have no user).
   *
   * @param toolId the tool the sources were registered for
   * @param userId the requesting user's opaque id (may be {@code null})
   * @param query the search text; blank returns every visible source for the tool
   * @return the matching sources' content joined by blank lines, or an empty string
   */
  String searchSources(String toolId, String userId, String query);
}
