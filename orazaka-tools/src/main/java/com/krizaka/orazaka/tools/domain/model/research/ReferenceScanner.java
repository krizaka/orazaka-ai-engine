package com.krizaka.orazaka.tools.domain.model.research;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The references an answer states in its prose — URLs, {@code owner/repo} repository names and
 * statute references (« art. 1904 », « RLRQ c. P-40.1 », « L.R.C. 1985 ») — so the verifier can
 * ask, for each one, whether a verified claim cites it (ADR-087 §3). An invented repository or URL
 * in prose is exactly the failure of krizaka/krizaka-com#365.
 */
public final class ReferenceScanner {

  private static final Pattern URL = Pattern.compile("https?://[^\\s)\\]>\"'«»]+");
  private static final Pattern REPOSITORY =
      Pattern.compile(
          "(?<![\\w./-])([A-Za-z0-9][A-Za-z0-9-]{0,38})/([A-Za-z0-9._-]{1,100})(?![\\w/])");
  private static final Pattern STATUTE =
      Pattern.compile(
          "(?i)\\b(?:art(?:icle)?s?\\.?\\s*\\d+[\\w.-]*|RLRQ\\s*,?\\s*c(?:h)?\\.?\\s*[A-Z]-?[\\d.]+|L\\.?R\\.?[CQ]\\.?\\s*\\d{4}|R\\.?S\\.?C\\.?\\s*\\d{4})");

  private ReferenceScanner() {}

  /**
   * The references stated in a text.
   *
   * @param text an answer's prose
   * @return URLs, repositories and statute references, in order of appearance
   */
  public static Set<String> references(String text) {
    Set<String> found = new LinkedHashSet<>();
    if (text == null) {
      return found;
    }
    Matcher urls = URL.matcher(text);
    while (urls.find()) {
      found.add(trimPunctuation(urls.group()));
    }
    String withoutUrls = URL.matcher(text).replaceAll(" ");
    Matcher repos = REPOSITORY.matcher(withoutUrls);
    while (repos.find()) {
      String candidate = repos.group();
      // a date (10/23) or a fraction is not a repository
      if (!candidate.matches("\\d+/\\d+")) {
        found.add(trimPunctuation(candidate));
      }
    }
    Matcher statutes = STATUTE.matcher(withoutUrls);
    while (statutes.find()) {
      found.add(trimPunctuation(statutes.group()).replaceAll("\\s+", " "));
    }
    return found;
  }

  private static String trimPunctuation(String s) {
    return s.replaceAll("[.,;:!?]+$", "");
  }
}
