package com.krizaka.orazaka.core.application.service;

import com.krizaka.orazaka.core.domain.model.chat.EngineTool;
import com.krizaka.orazaka.core.domain.model.mcp.McpToolInfo;
import com.krizaka.orazaka.core.domain.ports.inbound.McpService;
import com.krizaka.orazaka.core.domain.ports.outbound.ToolRegistry;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Concrete, package-private implementation of {@link McpService}. Reads the tools through the
 * {@link ToolRegistry} port.
 */
@Service
class McpServiceImpl implements McpService {

  private final ToolRegistry toolRegistry;

  public McpServiceImpl(ToolRegistry toolRegistry) {
    this.toolRegistry = Objects.requireNonNull(toolRegistry, "ToolRegistry must not be null");
  }

  @Override
  public List<McpToolInfo> getAvailableTools() {
    return toolRegistry.getRegisteredTools().stream()
        .map(tool -> new McpToolInfo(tool.name(), tool.description(), tool.inputSchema()))
        .toList();
  }

  @Override
  public String executeTool(String name, String argumentsJson) {
    EngineTool tool =
        toolRegistry.getRegisteredTools().stream()
            .filter(t -> t.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + name));

    return tool.call(argumentsJson);
  }
}
