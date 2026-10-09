package com.krizaka.orazaka.core.domain.model.image;

/**
 * What one image generation actually consumed.
 *
 * <p>The image twin of {@link com.krizaka.orazaka.core.domain.model.chat.TokenUsage}, and for the
 * same reason (ERR-127): this is the number image generation is billed on — {@code IMAGE_STEP},
 * which {@code ConsumptionReport} derives as {@code images × steps × megapixels} — and a value that
 * decides what a user is charged must not be reachable only by string key.
 *
 * <p>{@link #reported()} is the distinction {@code TokenUsage} draws, kept here: a generation whose
 * parameters are unknown is <b>unmeasured</b>, which releases the hold, and is a different fact
 * from one that consumed nothing.
 *
 * <p><b>Where each number comes from, and which one is a promise rather than an observation.</b>
 * {@code images}, {@code width} and {@code height} are decided by the adapter and sent to the
 * provider, so they are facts about the request. {@code steps} is <i>not</i> in the request: {@code
 * sd-server} takes it as a launch flag, so the meter reports the count the deployment was
 * configured with ({@code IMAGE_GEN_STEPS}, read by both the CLI that launches the server and the
 * adapter that meters it). One env var, two readers — and if they ever diverge, the bill is wrong
 * with nothing to say so. That is the weakest link in this capability's metering and ADR-047
 * records it as such.
 *
 * @param images images produced by the request
 * @param steps denoising steps per image
 * @param width output width in pixels
 * @param height output height in pixels
 */
public record ImageUsage(int images, int steps, int width, int height) {

  /**
   * Sentinel for a generation whose parameters were not reported. {@code -1} rather than {@code 0}
   * because the two are different facts: nothing measured, versus nothing consumed.
   */
  private static final int UNREPORTED = -1;

  private static final ImageUsage NONE =
      new ImageUsage(UNREPORTED, UNREPORTED, UNREPORTED, UNREPORTED);

  /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
  public ImageUsage {
    requireCountable(images, "images");
    requireCountable(steps, "steps");
    requireCountable(width, "width");
    requireCountable(height, "height");
  }

  private static void requireCountable(int value, String field) {
    if (value < UNREPORTED) {
      throw new IllegalArgumentException(field + " must be >= 0, or -1 when unreported");
    }
  }

  /**
   * @return the sentinel for a provider that reported nothing
   */
  public static ImageUsage none() {
    return NONE;
  }

  /**
   * @return whether the provider reported anything at all — false releases the hold rather than
   *     billing a guess
   */
  public boolean reported() {
    return images > UNREPORTED && steps > UNREPORTED && width > UNREPORTED && height > UNREPORTED;
  }
}
