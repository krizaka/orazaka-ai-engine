package com.orazaka.core.domain.model.image;

import java.util.Arrays;
import java.util.Objects;

/**
 * Unified image response record.
 *
 * @param imageData The raw binary data of the generated image (may be null if URL is used).
 * @param url The public URL of the generated image.
 * @param format The image file format (e.g., "png", "jpg").
 * @param usage What the generation consumed, for settlement (ADR-047); never null.
 */
public record ImageResponse(byte[] imageData, String url, String format, ImageUsage usage) {

  /**
   * Compact canonical constructor: an absent usage is the "nothing reported" sentinel, not null.
   */
  public ImageResponse {
    usage = usage == null ? ImageUsage.none() : usage;
  }

  /**
   * An image whose generation parameters are unknown.
   *
   * <p>Kept as a three-argument constructor because only one production site builds a measured
   * response, while every fallback and test builds an unmeasured one — and an unmeasured image
   * releases its hold rather than billing a guess.
   *
   * @param imageData the raw bytes
   * @param url the public URL
   * @param format the file format
   */
  public ImageResponse(byte[] imageData, String url, String format) {
    this(imageData, url, format, ImageUsage.none());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o
        instanceof
        ImageResponse(
            byte[] otherData,
            String otherUrl,
            String otherFormat,
            ImageUsage otherUsage))) return false;
    return Arrays.equals(imageData, otherData)
        && Objects.equals(url, otherUrl)
        && Objects.equals(format, otherFormat)
        && Objects.equals(usage, otherUsage);
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(imageData);
    result = 31 * result + Objects.hashCode(url);
    result = 31 * result + Objects.hashCode(format);
    result = 31 * result + Objects.hashCode(usage);
    return result;
  }

  @Override
  public String toString() {
    int dataLen = imageData != null ? imageData.length : 0;
    return "ImageResponse[imageData="
        + dataLen
        + " bytes, url="
        + url
        + ", format="
        + format
        + ", usage="
        + usage
        + "]";
  }
}
