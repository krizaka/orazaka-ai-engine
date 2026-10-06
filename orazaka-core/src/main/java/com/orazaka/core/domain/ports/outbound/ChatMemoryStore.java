package com.orazaka.core.domain.ports.outbound;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * Outbound port for durable conversation memory — the chronological transcript of a single
 * conversation. Implemented by an application-layer adapter over the persistence module. Used by
 * {@link MemoryResolver} so multi-turn context survives application restarts (the previous
 * in-memory store was lost on every restart).
 */
public interface ChatMemoryStore {

  /** Last {@code limit} messages of a conversation, chronological (oldest-first). */
  List<Message> findRecent(String conversationId, int limit);

  /** Appends messages to a conversation's transcript in order. */
  void append(String conversationId, List<Message> messages);

  /** Removes a conversation's whole transcript. */
  void clear(String conversationId);
}
