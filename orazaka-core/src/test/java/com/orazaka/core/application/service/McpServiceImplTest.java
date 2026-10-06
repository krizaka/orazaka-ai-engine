package com.orazaka.core.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.orazaka.core.domain.model.mcp.McpToolInfo;
import com.orazaka.core.domain.ports.outbound.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

@ExtendWith(MockitoExtension.class)
class McpServiceImplTest {

  @Mock private ToolRegistry toolRegistry;
  @Mock private ToolCallback toolCallback;
  @Mock private ToolDefinition toolDefinition;

  private McpServiceImpl mcpService;

  @BeforeEach
  void setUp() {
    mcpService = new McpServiceImpl(toolRegistry);
  }

  @Test
  void shouldReturnAvailableTools() {
    when(toolDefinition.name()).thenReturn("testTool");
    when(toolDefinition.description()).thenReturn("A test tool description");
    when(toolDefinition.inputSchema()).thenReturn("{\"type\":\"object\"}");
    when(toolCallback.getToolDefinition()).thenReturn(toolDefinition);
    when(toolRegistry.getRegisteredTools()).thenReturn(List.of(toolCallback));

    List<McpToolInfo> tools = mcpService.getAvailableTools();

    assertThat(tools).hasSize(1);
    McpToolInfo toolInfo = tools.get(0);
    assertThat(toolInfo.name()).isEqualTo("testTool");
    assertThat(toolInfo.description()).isEqualTo("A test tool description");
    assertThat(toolInfo.inputSchema()).isEqualTo("{\"type\":\"object\"}");
  }

  @Test
  void shouldExecuteToolSuccessfully() {
    when(toolDefinition.name()).thenReturn("testTool");
    when(toolCallback.getToolDefinition()).thenReturn(toolDefinition);
    when(toolCallback.call("{\"arg\":\"val\"}")).thenReturn("execution result");
    when(toolRegistry.getRegisteredTools()).thenReturn(List.of(toolCallback));

    String result = mcpService.executeTool("testTool", "{\"arg\":\"val\"}");

    assertThat(result).isEqualTo("execution result");
    verify(toolCallback).call("{\"arg\":\"val\"}");
  }

  @Test
  void shouldThrowWhenExecutingNonexistentTool() {
    when(toolDefinition.name()).thenReturn("testTool");
    when(toolCallback.getToolDefinition()).thenReturn(toolDefinition);
    when(toolRegistry.getRegisteredTools()).thenReturn(List.of(toolCallback));

    assertThatThrownBy(() -> mcpService.executeTool("nonexistent", "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Tool not found: nonexistent");
  }
}
