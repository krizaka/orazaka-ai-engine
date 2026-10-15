package com.krizaka.orazaka.assets.domain.port;

import java.util.regex.Pattern;

/**
 * The one layout of the durable store: {@code t/<tenant>/u/<user>/jobs/<jobId>/<file>}.
 *
 * <p>Stable ids only — never an e-mail, handle or name — so that deleting a user or a tenant is
 * deleting a prefix. Every segment is validated: a key is built from request-adjacent values and
 * must not be able to climb out of its owner's prefix.
 */
public final class AssetKeys {

  private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

  private AssetKeys() {}

  /**
   * The key of one job output.
   *
   * @param tenant the tenant id (a deployment with one tenant uses one fixed id)
   * @param userId the owner of the job
   * @param jobId the job
   * @param file the file name within the job's output
   * @return the object key
   * @throws IllegalArgumentException if a segment is not a plain id or file name
   */
  public static String jobFile(String tenant, String userId, String jobId, String file) {
    return jobPrefix(tenant, userId, jobId) + segment("file", file);
  }

  /**
   * The prefix holding every file of a job; ends with {@code /}.
   *
   * @param tenant the tenant id
   * @param userId the owner of the job
   * @param jobId the job
   * @return the prefix
   */
  public static String jobPrefix(String tenant, String userId, String jobId) {
    return "t/"
        + segment("tenant", tenant)
        + "/u/"
        + segment("user", userId)
        + "/jobs/"
        + segment("job", jobId)
        + "/";
  }

  private static String segment(String what, String value) {
    if (value == null || value.contains("..") || !SEGMENT.matcher(value).matches()) {
      throw new IllegalArgumentException("not a valid " + what + " segment for a storage key");
    }
    return value;
  }
}
