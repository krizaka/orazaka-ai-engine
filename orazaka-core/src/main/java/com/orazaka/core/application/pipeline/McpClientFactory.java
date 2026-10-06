package com.orazaka.core.application.pipeline;

import com.orazaka.core.domain.model.chat.InternalChatRequest;
import com.orazaka.core.domain.ports.outbound.PlatformMcpServerProvider;
import com.orazaka.core.domain.ports.outbound.ToolRegistry;
import com.orazaka.core.domain.ports.outbound.UserMcpServerProvider;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.json.JsonMapper;

/**
 * Factory responsible for building MCP client connections and resolving tool callbacks from
 * platform, user, and local tool registry sources.
 *
 * <p>Extracted from {@link EnginePipelineBridge} to enforce single-responsibility and keep the
 * bridge focused on prompt compilation orchestration. Follows ERR-107 (Mapper Isolation Invariant
 * applied to factory patterns).
 */
final class McpClientFactory {

  private static final Logger logger = LoggerFactory.getLogger(McpClientFactory.class);

  private McpClientFactory() {}

  /** Describes an MCP server connection for client initialization. */
  record McpServerDescriptor(
      String label,
      String url,
      String authToken,
      String transportType,
      String command,
      String args) {

    static McpServerDescriptor fromPlatform(PlatformMcpServerProvider.PlatformMcpServer server) {
      return new McpServerDescriptor(
          server.label(),
          server.url(),
          server.authToken(),
          server.transportType(),
          server.command(),
          server.args());
    }

    static McpServerDescriptor fromUser(UserMcpServerProvider.UserMcpServer server) {
      return new McpServerDescriptor(
          server.label(), server.url(), server.authToken(), "SSE", null, null);
    }
  }

  /**
   * Resolves all MCP tool callbacks from platform servers, user servers, and local registry.
   *
   * @param request The chat request (used to extract user context for user MCP servers).
   * @param platformMcpServerProvider The platform-wide MCP provider (nullable).
   * @param userMcpServerProvider The user private MCP provider (nullable).
   * @param toolRegistry The local tools registry (nullable).
   * @param closeables Mutable list to track closeable MCP clients for cleanup.
   * @return Combined list of all tool callbacks.
   */
  static List<ToolCallback> resolveToolCallbacks(
      InternalChatRequest request,
      PlatformMcpServerProvider platformMcpServerProvider,
      UserMcpServerProvider userMcpServerProvider,
      ToolRegistry toolRegistry,
      List<AutoCloseable> closeables) {

    List<ToolCallback> allToolCallbacks = new ArrayList<>();

    if (toolRegistry != null) {
      allToolCallbacks.addAll(toolRegistry.getRegisteredTools());
    }

    // Platform MCP servers
    if (platformMcpServerProvider != null) {
      for (PlatformMcpServerProvider.PlatformMcpServer server :
          platformMcpServerProvider.getActivePlatformMcpServers()) {
        if (Boolean.TRUE.equals(server.enabled())) {
          buildClientSafely(McpServerDescriptor.fromPlatform(server), closeables, allToolCallbacks);
        }
      }
    }

    // User private MCP servers
    String currentUserId = request.context().userId();
    if (userMcpServerProvider != null) {
      for (UserMcpServerProvider.UserMcpServer server :
          userMcpServerProvider.getActiveUserMcpServers(currentUserId)) {
        if (Boolean.TRUE.equals(server.enabled())) {
          buildClientSafely(McpServerDescriptor.fromUser(server), closeables, allToolCallbacks);
        }
      }
    }
    return allToolCallbacks;
  }

  /**
   * Safely initializes an MCP client and registers its tool callbacks. Failures are logged and
   * silently skipped (resilient to server downtime).
   */
  private static void buildClientSafely(
      McpServerDescriptor descriptor,
      List<AutoCloseable> closeables,
      List<ToolCallback> callbacks) {
    try {
      McpSyncClient client =
          buildClient(
              descriptor.url(),
              descriptor.authToken(),
              descriptor.transportType(),
              descriptor.command(),
              descriptor.args());
      closeables.add(client);
      callbacks.addAll(SyncMcpToolCallbackProvider.syncToolCallbacks(List.of(client)));
      logger.info("Successfully initialized MCP client: {}", descriptor.label());
    } catch (Exception e) {
      logger.error("Failed to initialize MCP client: {}", descriptor.label(), e);
    }
  }

  /**
   * Unified MCP client builder supporting both LOCAL (Stdio) and SSE transports.
   *
   * @param url The SSE server URL (used for SSE transport).
   * @param authToken Optional bearer token for SSE transport authentication.
   * @param transportType Transport type: "LOCAL" for Stdio, anything else for SSE.
   * @param command The command for LOCAL transport (e.g. "npx").
   * @param args Comma-separated arguments for LOCAL transport.
   * @return An initialized {@link McpSyncClient}.
   */
  private static McpSyncClient buildClient(
      String url, String authToken, String transportType, String command, String args) {
    McpClientTransport transport;
    if ("LOCAL".equalsIgnoreCase(transportType)) {
      List<String> argsList = new ArrayList<>();
      if (args != null && !args.isBlank()) {
        for (String arg : args.split(",")) {
          argsList.add(arg.trim());
        }
      }
      McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new JsonMapper());
      ServerParameters params = ServerParameters.builder(command).args(argsList).build();
      transport = new StdioClientTransport(params, jsonMapper);
    } else {
      HttpClientSseClientTransport.Builder transportBuilder =
          HttpClientSseClientTransport.builder(url);
      if (authToken != null && !authToken.isBlank()) {
        // MCP SDK 2.0: customizeRequest(Consumer) →
        // httpRequestCustomizer(McpSyncHttpClientRequestCustomizer).
        transportBuilder.httpRequestCustomizer(
            (requestBuilder, method, uri, body, context) ->
                requestBuilder.header("Authorization", authToken));
      }
      transport = transportBuilder.build();
    }
    McpSyncClient client = McpClient.sync(transport).build();
    client.initialize();
    return client;
  }
}
