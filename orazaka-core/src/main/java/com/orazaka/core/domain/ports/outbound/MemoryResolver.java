package com.orazaka.core.domain.ports.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Thread-safe resolver for session-scoped {@link ChatMemory} instances.
 *
 * <p>Ensures strict multi-session partitioning by mapping each {@code conversationId} to an
 * isolated {@link ChatMemory} store. This guarantees that conversation history never leaks across
 * different user sessions or concurrent threads, satisfying the Guardian Protocol defined in
 * AGENTS.md §3.A.
 *
 * <p>Internal storage uses a {@link ConcurrentHashMap} making this component fully safe for
 * concurrent access by Java 21 Virtual Threads.
 *
 * @see org.springframework.ai.chat.memory.ChatMemory
 */
@Component
public class MemoryResolver {

  private final Map<String, ChatMemory> sessionMemories = new ConcurrentHashMap<>();

  /**
   * Durable conversation store (Postgres). When present, history survives application restarts;
   * when {@code null} (no persistence wired, e.g. unit tests) the in-process map is used instead.
   */
  private final ChatMemoryStore store;

  /** Transient/in-memory resolver — used by unit tests and when no durable store is wired. */
  public MemoryResolver() {
    this.store = null;
  }

  /** Spring constructor: binds the durable {@link ChatMemoryStore} when one is on the classpath. */
  @Autowired
  public MemoryResolver(ObjectProvider<ChatMemoryStore> storeProvider) {
    this.store = storeProvider.getIfAvailable();
  }

  /**
   * Resolves an isolated {@link ChatMemory} for the given conversation.
   *
   * <p>If {@code conversationId} is {@code null} or blank, a transient (non-stored) {@link
   * InMemoryChatMemory} is returned — suitable for one-off stateless calls. Otherwise, when a
   * durable {@link ChatMemoryStore} is configured the history is read from / written to Postgres
   * (surviving restarts); without one it falls back to the per-conversation in-process map.
   *
   * <p>This method is safe for concurrent Virtual Thread access due to the underlying {@link
   * ConcurrentHashMap#computeIfAbsent} atomicity guarantee.
   *
   * @param conversationId The unique session identifier. May be {@code null} for stateless calls.
   * @return The {@link ChatMemory} bound to the given conversation, or a fresh transient instance.
   */
  public ChatMemory resolve(String conversationId) {
    if (conversationId == null || conversationId.isBlank()) {
      return new InMemoryChatMemory();
    }
    if (store != null) {
      return new PersistentChatMemory(store, conversationId);
    }
    return sessionMemories.computeIfAbsent(conversationId, id -> new InMemoryChatMemory());
  }

  /**
   * Removes and discards the {@link ChatMemory} associated with the given conversation.
   *
   * <p>Should be called when a user explicitly closes a conversation thread or when session
   * eviction is triggered by a TTL policy. This method is idempotent — calling it with an unknown
   * {@code conversationId} has no effect.
   *
   * @param conversationId The unique session identifier to purge. No-op if not found.
   */
  public void purge(String conversationId) {
    if (store != null && conversationId != null && !conversationId.isBlank()) {
      store.clear(conversationId);
    }
    sessionMemories.remove(conversationId);
  }

  /**
   * {@link ChatMemory} view over the durable {@link ChatMemoryStore}, scoped to one conversation.
   * Reads the recent window from and appends turns to Postgres, so multi-turn context survives an
   * application restart (the in-memory fallback below does not).
   */
  private static final class PersistentChatMemory implements ChatMemory {

    /** Recent-window cap, matching {@link InMemoryChatMemory}: 50 messages ≈ 25 turns. */
    private static final int MAX_MESSAGES = 50;

    private final ChatMemoryStore store;
    private final String conversationId;

    private PersistentChatMemory(ChatMemoryStore store, String conversationId) {
      this.store = store;
      this.conversationId = conversationId;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
      store.append(this.conversationId, messages);
    }

    @Override
    public List<Message> get(String conversationId) {
      return store.findRecent(this.conversationId, MAX_MESSAGES);
    }

    @Override
    public void clear(String conversationId) {
      store.clear(this.conversationId);
    }
  }

  /**
   * In-memory {@link ChatMemory} implementation backed by a simple {@link ArrayList}.
   *
   * <p>Each instance is scoped to a single conversation. This implementation is not thread-safe on
   * its own, but concurrent access is prevented by the {@link MemoryResolver} which creates one
   * instance per {@code conversationId} atomically.
   *
   * <p><strong>Note:</strong> All messages are stored in-process. State is lost on application
   * restart unless an external persistence layer (e.g., Redis, PostgreSQL) is configured.
   */
  private static class InMemoryChatMemory implements ChatMemory {

    /**
     * Maximum conversation window size. 50 messages ≈ 25 user/assistant turns. Prevents unbounded
     * context growth, which would cause token overflow on long conversations. Oldest messages are
     * evicted FIFO when the limit is exceeded.
     */
    private static final int MAX_MESSAGES = 50;

    private final List<Message> messages = new ArrayList<>();

    /**
     * Appends messages to this conversation's history, evicting oldest messages if the window limit
     * is exceeded.
     *
     * @param conversationId The conversation identifier (informational; scoping is handled
     *     externally).
     * @param messages The list of {@link Message} objects to append.
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
      this.messages.addAll(messages);
      while (this.messages.size() > MAX_MESSAGES) {
        this.messages.remove(0);
      }
    }

    /**
     * Returns a defensive copy of all messages in this conversation.
     *
     * @param conversationId The conversation identifier (informational; scoping is handled
     *     externally).
     * @return A new {@link ArrayList} containing all stored messages.
     */
    @Override
    public List<Message> get(String conversationId) {
      return new ArrayList<>(messages);
    }

    /**
     * Clears all messages from this conversation's history.
     *
     * @param conversationId The conversation identifier (informational; scoping is handled
     *     externally).
     */
    @Override
    public void clear(String conversationId) {
      messages.clear();
    }
  }
}
