package com.krizaka.orazaka.core.domain.ports.outbound;

import com.krizaka.orazaka.core.domain.model.AssertionContract;

/**
 * Outbound port interface defining the contract for Tier D (Test-Driven Response) assertion
 * generation.
 *
 * <p>Implemented by a concrete adapter in {@code orazaka-interceptor-validation} that spins up a
 * fast reasoning model (e.g., Qwen-2.5-Coder) to generate structured test assertion schemas. The
 * generated {@link AssertionContract} is then used by the {@code ClosedLoopValidationInterceptor}
 * to validate LLM responses.
 *
 * @since 1.1.0
 */
public interface TestShaperPort {

  /**
   * Generates a set of test assertions based on the user's original prompt and the LLM response.
   *
   * <p>The implementation should analyze the prompt intent and produce machine-evaluable assertion
   * rules that the response must satisfy. If assertion generation fails, implementations must
   * return {@link AssertionContract#empty()} rather than throwing.
   *
   * @param userPrompt The original user prompt providing intent context.
   * @param responseText The LLM-generated response to validate.
   * @return A structured assertion contract, or {@link AssertionContract#empty()} on failure.
   */
  AssertionContract generateAssertions(String userPrompt, String responseText);
}
