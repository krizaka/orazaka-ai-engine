package com.krizaka.orazaka.tools.domain.model.law;

import java.util.List;

/**
 * The outcome of one watch run.
 *
 * @param unchanged articles confirmed with the same hash
 * @param stored articles stored for the first time, with no request (first import of an instrument)
 * @param requests URLs of the review requests opened
 * @param failures one line per change whose request could not be opened; it stays unapplied and is
 *     detected again by the next run
 */
public record LawWatchReport(
    int unchanged, int stored, List<String> requests, List<String> failures) {

  public LawWatchReport {
    requests = List.copyOf(requests);
    failures = List.copyOf(failures);
  }
}
