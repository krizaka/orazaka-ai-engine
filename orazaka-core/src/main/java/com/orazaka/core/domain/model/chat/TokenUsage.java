package com.orazaka.core.domain.model.chat;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * What one inference actually consumed, in tokens.
 *
 * <p>A typed record rather than three entries in a metadata map (ERR-127): this is the number chat
 * is billed on (ADR-033 §7, {@code KILOTOKEN}), and a value that decides what a user is charged
 * must not be reachable only by string key, where a rename is a silent revenue change and an absent
 * key is indistinguishable from zero consumption.
 *
 * <p>{@link #reported()} is that distinction made explicit: a provider that returns no usage block
 * is unmeasured, which releases the hold, while a genuine zero is measured and settles at the
 * pricebook's floor.
 *
 * @param promptTokens tokens in the prompt sent to the model
 * @param completionTokens tokens the model generated
 * @param totalTokens the provider's own total, which is not always the sum — some providers count
 *     cached or reasoning tokens that appear in neither half, so it is recorded as reported rather
 *     than re-derived
 */
public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {

  private static final BigDecimal TOKENS_PER_KILOTOKEN = new BigDecimal("1000");

  /**
   * Sentinel for an inference whose provider reported no usage at all. {@code -1} rather than
   * {@code 0} because the two are different facts: nothing measured, versus nothing consumed.
   */
  private static final int UNREPORTED = -1;

  private static final TokenUsage NONE = new TokenUsage(UNREPORTED, UNREPORTED, UNREPORTED);

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public TokenUsage {
    requireCountable(promptTokens, "promptTokens");
    requireCountable(completionTokens, "completionTokens");
    requireCountable(totalTokens, "totalTokens");
  }

  private static void requireCountable(int tokens, String field) {
    if (tokens < UNREPORTED) {
      throw new IllegalArgumentException(field + " must be >= 0, or -1 when unreported");
    }
  }

  /**
   * The usage of an inference whose provider reported none.
   *
   * @return the unreported sentinel
   */
  public static TokenUsage none() {
    return NONE;
  }

  /**
   * Records what a provider reported, defaulting each missing half to zero.
   *
   * @param promptTokens tokens in the prompt, or {@code null} if the provider omitted it
   * @param completionTokens tokens generated, or {@code null} if the provider omitted it
   * @param totalTokens the provider's total, or {@code null} to sum the two halves
   * @return the reported usage
   */
  public static TokenUsage reported(
      Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    int prompt = promptTokens == null ? 0 : promptTokens;
    int completion = completionTokens == null ? 0 : completionTokens;
    return new TokenUsage(
        prompt, completion, totalTokens == null ? prompt + completion : totalTokens);
  }

  /**
   * Whether the provider reported usage at all.
   *
   * @return {@code false} when nothing was reported — an unmeasured turn, which releases its hold
   *     rather than being billed at an estimate
   */
  public boolean reported() {
    return promptTokens != UNREPORTED;
  }

  /**
   * This turn's consumption in the unit chat is priced in.
   *
   * @return total tokens as kilotokens, or zero when nothing was reported
   */
  public BigDecimal kilotokens() {
    if (!reported()) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(totalTokens).divide(TOKENS_PER_KILOTOKEN, MathContext.DECIMAL64);
  }
}
