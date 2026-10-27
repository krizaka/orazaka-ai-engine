package com.krizaka.orazaka.core.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.krizaka.orazaka.core.domain.model.chat.EngineTool;
import com.krizaka.orazaka.core.domain.model.mcp.McpToolInfo;
import com.krizaka.orazaka.core.domain.ports.outbound.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpServiceImplTest {

  private final ToolRegistry toolRegistry = mock(ToolRegistry.class);

  private McpServiceImpl mcpService;

  private static EngineTool testTool() {
    return new EngineTool(
        "testTool",
        "A test tool description",
        "{\"type\":\"object\"}",
        json -> "{\"arg\":\"val\"}".equals(json) ? "execution result" : "unexpected " + json,
        null);
  }

  @BeforeEach
  void setUp() {
    mcpService = new McpServiceImpl(toolRegistry);
  }

  @Test
  void shouldReturnAvailableTools() {
    when(toolRegistry.getRegisteredTools()).thenReturn(List.of(testTool()));

    List<McpToolInfo> tools = mcpService.getAvailableTools();

    assertThat(tools).hasSize(1);
    McpToolInfo toolInfo = tools.getFirst();
    assertThat(toolInfo.name()).isEqualTo("testTool");
    assertThat(toolInfo.description()).isEqualTo("A test tool description");
    assertThat(toolInfo.inputSchema()).isEqualTo("{\"type\":\"object\"}");
  }

  @Test
  void shouldExecuteToolSuccessfully() {
    when(toolRegistry.getRegisteredTools()).thenReturn(List.of(testTool()));

    String result = mcpService.executeTool("testTool", "{\"arg\":\"val\"}");

    assertThat(result).isEqualTo("execution result");
  }

  @Test
  void shouldThrowWhenExecutingNonexistentTool() {
    when(toolRegistry.getRegisteredTools()).thenReturn(List.of(testTool()));

    assertThatThrownBy(() -> mcpService.executeTool("nonexistent", "{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Tool not found: nonexistent");
  }
}
