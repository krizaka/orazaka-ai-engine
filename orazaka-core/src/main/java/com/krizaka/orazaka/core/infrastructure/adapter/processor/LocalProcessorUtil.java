package com.krizaka.orazaka.core.infrastructure.adapter.processor;

/**
 * Stateless, dependency-free helpers shared by the local audio/video processors.
 *
 * <p>Provider URL resolution (which needs the catalog) moved to the injectable {@link
 * ProviderEndpointResolver} [ERR-127]; only pure functions remain here.
 */
final class LocalProcessorUtil {

  private LocalProcessorUtil() {}

  /** Returns {@code true} if every byte in the array is zero. */
  static boolean isAllZeros(byte[] data) {
    for (byte b : data) {
      if (b != 0) return false;
    }
    return true;
  }

  /**
   * Resolves the Whisper model name, normalizing any variant containing "whisper" to "whisper-1".
   *
   * @param requestModel explicit model from the request (may be null).
   * @param defaultModel the configured default model.
   * @return the resolved model name.
   */
  static String resolveWhisperModel(String requestModel, String defaultModel) {
    String finalModel =
        requestModel != null && !requestModel.isBlank() ? requestModel : defaultModel;
    if (finalModel != null && finalModel.toLowerCase().contains("whisper")) {
      return "whisper-1";
    }
    return finalModel;
  }
}
