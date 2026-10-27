package com.krizaka.orazaka.core.domain.ports.outbound;

import com.krizaka.orazaka.core.domain.model.chat.InternalChatRequest;
import java.util.List;

/**
 * Resolves the tools a turn may call, in the provider library's own handles the core never opens
 * (#121). The local tools, the platform's MCP servers and the user's own come through here.
 *
 * <p>ADR-091 holds behind this port: the roles of a tool and the audit of a denial are checked at
 * the tool itself, and a turn with no verified actor gets none.
 */
public interface ToolResolution {

  /**
   * The tools of one turn.
   *
   * @param handles the tool handles, opaque to the core; handed back to {@link
   *     PromptAssembly#assemble}
   * @param closeables the connections opened to resolve them, closed when the turn ends
   */
  record ResolvedTools(List<?> handles, List<AutoCloseable> closeables) {

    /** No tool, nothing to close. */
    public static ResolvedTools none() {
      return new ResolvedTools(List.of(), List.of());
    }
  }

  /**
   * Resolves the tools the request's actor may use.
   *
   * @param request the chat request (its context names the actor and the user)
   * @return the tools; never {@code null}, empty when the turn has no verified actor
   */
  ResolvedTools resolve(InternalChatRequest request);
}
