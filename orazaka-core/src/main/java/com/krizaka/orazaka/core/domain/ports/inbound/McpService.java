package com.krizaka.orazaka.core.domain.ports.inbound;

import com.krizaka.orazaka.core.domain.model.mcp.McpToolInfo;
import java.util.List;

/** Inbound port interface for interacting with and executing MCP and local tools. */
public interface McpService {

  /**
   * Retrieves all available registered tools in the system.
   *
   * @return A list of {@link McpToolInfo} representing the tools.
   */
  List<McpToolInfo> getAvailableTools();

  /**
   * Executes a registered tool by its name with the given JSON arguments.
   *
   * @param name The name of the tool to execute.
   * @param argumentsJson The JSON string containing tool arguments.
   * @return The execution result string.
   */
  String executeTool(String name, String argumentsJson);
}
