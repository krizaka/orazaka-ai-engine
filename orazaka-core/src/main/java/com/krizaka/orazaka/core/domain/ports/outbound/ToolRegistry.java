package com.krizaka.orazaka.core.domain.ports.outbound;

import com.krizaka.orazaka.core.domain.model.chat.EngineTool;
import java.util.List;
import java.util.function.Function;

/**
 * Registry interface for local Java methods to be used as tools by the AI models. Pure Java: the
 * adapter turns each registration into the model library's tool (ADR-092 §4 rule 1).
 */
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
   * Retrieves all registered tools.
   *
   * @return The registered {@link EngineTool}s.
   */
  List<EngineTool> getRegisteredTools();
}
