package com.krizaka.orazaka.adapter.springai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.orazaka.core.domain.model.chat.EngineTool;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class SpringAiToolRegistryTest {

  record Echo(String text) {}

  @Test
  @DisplayName(
      "a registered function comes back as an engine tool that runs and keeps its callback")
  void registeredFunctionIsAnEngineTool() {
    SpringAiToolRegistry registry = new SpringAiToolRegistry(null, null);
    registry.registerTool("echo", "Repeats the text", Echo.class, in -> "heard " + in.text());

    List<EngineTool> tools = registry.getRegisteredTools();

    assertThat(tools)
        .singleElement()
        .satisfies(
            tool -> {
              assertThat(tool.name()).isEqualTo("echo");
              assertThat(tool.description()).isEqualTo("Repeats the text");
              assertThat(tool.inputSchema()).contains("text");
              assertThat(tool.nativeTool()).isInstanceOf(ToolCallback.class);
              assertThat(tool.call("{\"text\":\"hi\"}")).contains("heard hi");
            });
  }
}
