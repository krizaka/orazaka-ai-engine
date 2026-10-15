package com.krizaka.orazaka.assets.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the second, durable copy of an asset lives ({@code media.storage}, i.e. the environment
 * variables {@code MEDIA_STORAGE_*}).
 *
 * @param provider {@code local} (default: the disk is the only copy), {@code bunny} or {@code
 *     directory}
 * @param zone the Bunny storage zone (private)
 * @param apiKey the zone's storage password; never logged, never in {@code toString}
 * @param endpoint the Bunny storage host
 * @param directory the target of the {@code directory} provider
 * @param tenant the first segment of every key; a single-tenant deployment keeps one fixed id
 */
@ConfigurationProperties(prefix = "media.storage")
public record MediaStorageProperties(
    @DefaultValue("local") String provider,
    String zone,
    String apiKey,
    @DefaultValue("storage.bunnycdn.com") String endpoint,
    String directory,
    @DefaultValue("default") String tenant) {

  /** Fails at start, by name, on an incomplete provider — never at the first upload. */
  public MediaStorageProperties {
    switch (provider) {
      case "local" -> {}
      case "bunny" -> {
        if (isBlank(zone) || isBlank(apiKey)) {
          throw new IllegalArgumentException(
              "media.storage.provider=bunny needs MEDIA_STORAGE_ZONE and MEDIA_STORAGE_API_KEY");
        }
      }
      case "directory" -> {
        if (isBlank(directory)) {
          throw new IllegalArgumentException(
              "media.storage.provider=directory needs MEDIA_STORAGE_DIRECTORY");
        }
      }
      default ->
          throw new IllegalArgumentException(
              "media.storage.provider must be local, bunny or directory, was " + provider);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  @Override
  public String toString() {
    return "MediaStorageProperties[provider=" + provider + ", zone=" + zone + ", apiKey=***]";
  }
}
