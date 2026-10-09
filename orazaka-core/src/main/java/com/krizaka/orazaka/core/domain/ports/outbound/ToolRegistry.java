package com.krizaka.orazaka.core.domain.ports.outbound;

import java.util.List;
import java.util.function.Function;
import org.springframework.ai.tool.ToolCallback;

/** Registry interface for local Java methods to be used as tools by the AI models. */
public interface ToolRegistry {

  /**
   * Registers a local Java method as a tool.
   *
   * @param name Name of the function.
   * @param description Description of what the function does.
   * @param inputType Input class.
   * @param function The actual implementation.
   * @param <I> Input type.
   * @param <O> Output type.
   */
  <I, O> void registerTool(
      String name, String description, Class<I> inputType, Function<I, O> function);

  /**
   * Retrieves all registered tool callbacks.
   *
   * @return A list of {@link ToolCallback} objects.
   */
  List<ToolCallback> getRegisteredTools();
}
