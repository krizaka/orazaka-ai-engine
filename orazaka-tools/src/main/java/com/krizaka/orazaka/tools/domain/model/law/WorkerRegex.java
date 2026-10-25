package com.krizaka.orazaka.tools.domain.model.law;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A rule's pattern as the pack's worker will run it. The worker is Python ({@code re}, Unicode by
 * default), so a pattern is accepted only when it compiles here with Unicode character classes,
 * uses none of the constructs one engine reads differently from the other, and cannot backtrack
 * exponentially (the linter of orazaka-packs {@code test_regex_safety.py}, same shape).
 */
final class WorkerRegex {

  /** Escapes Java reads and the worker does not, or reads otherwise. */
  private static final String NOT_PORTABLE_ESCAPES = "pPhHRXQEGzvV";

  /**
   * A group whose body holds an unbounded quantifier and that is itself repeated without bound, as
   * {@code (a+)+}: the shape the pack's linter refuses.
   */
  private static final Pattern NESTED =
      Pattern.compile(
          "\\((?:\\?:)?(?:[^()\\\\]|\\\\.)*(?:[+*]|\\{\\d+,\\})(?:[^()\\\\]|\\\\.)*\\)(?:[+*]|\\{\\d+,\\})");

  /** A group that sets flags for the rest of the pattern, as {@code (?i)}. */
  private static final Pattern GLOBAL_FLAGS = Pattern.compile("\\(\\?[a-zA-Z]+\\)");

  private WorkerRegex() {}

  /**
   * Compiles a pattern the way the worker reads it.
   *
   * @param pattern the pattern
   * @return the compiled pattern
   * @throws PatternSyntaxException when it does not compile
   */
  static Pattern compile(String pattern) {
    return Pattern.compile(pattern, Pattern.UNICODE_CHARACTER_CLASS);
  }

  /**
   * Why a pattern cannot be run by the worker, if it cannot.
   *
   * @param pattern the pattern
   * @return the reason in words, or empty when it is usable
   */
  static Optional<String> problem(String pattern) {
    try {
      compile(pattern);
    } catch (PatternSyntaxException invalid) {
      return Optional.of("does not compile: " + invalid.getDescription());
    }
    Optional<String> construct = notPortable(pattern);
    if (construct.isPresent()) {
      return construct;
    }
    if (NESTED.matcher(pattern).find()) {
      return Optional.of("nests unbounded quantifiers, which can backtrack exponentially");
    }
    return Optional.empty();
  }

  /**
   * Whether a pattern finds something in the empty text, and so in every document.
   *
   * @param pattern a pattern {@link #problem} accepts
   * @return true when it matches the empty string
   */
  static boolean matchesEmpty(String pattern) {
    return compile(pattern).matcher("").find();
  }

  /**
   * Whether a pattern finds something in a text.
   *
   * @param pattern a pattern {@link #problem} accepts
   * @param text the text
   * @return true when found
   */
  static boolean finds(String pattern, String text) {
    return compile(pattern).matcher(text).find();
  }

  private static Optional<String> notPortable(String pattern) {
    boolean inClass = false;
    boolean afterQuantifier = false;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '\\' && i + 1 < pattern.length()) {
        char escaped = pattern.charAt(++i);
        if (NOT_PORTABLE_ESCAPES.indexOf(escaped) >= 0) {
          return Optional.of("uses \\" + escaped + ", which the worker reads differently");
        }
        afterQuantifier = false;
        continue;
      }
      if (inClass) {
        if (c == '&' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '&') {
          return Optional.of(
              "intersects classes with &&, which the worker reads as two characters");
        }
        inClass = c != ']';
        continue;
      }
      if (c == '+' && afterQuantifier) {
        return Optional.of("uses a possessive quantifier, which the worker may not read");
      }
      Optional<String> group = groupProblem(pattern.substring(i), i);
      if (group.isPresent()) {
        return group;
      }
      inClass = c == '[';
      afterQuantifier = c == '*' || c == '+' || c == '?' || c == '}';
    }
    return Optional.empty();
  }

  private static Optional<String> groupProblem(String rest, int at) {
    if (rest.startsWith("(?<") && !rest.startsWith("(?<=") && !rest.startsWith("(?<!")) {
      return Optional.of("names a group with (?<name>), which the worker does not read");
    }
    if (rest.startsWith("(?>")) {
      return Optional.of("uses an atomic group (?>, which the worker may not read");
    }
    if (at > 0 && GLOBAL_FLAGS.matcher(rest).lookingAt()) {
      return Optional.of("sets a flag after the start, which the worker refuses");
    }
    return Optional.empty();
  }
}
