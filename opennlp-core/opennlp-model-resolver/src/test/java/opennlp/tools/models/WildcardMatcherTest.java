/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.tools.models;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class WildcardMatcherTest {

  private static final String SMILEY = "\uD83D\uDE00";

  private static Stream<Arguments> accepted() {
    return Stream.of(
        Arguments.of("*", ""),
        Arguments.of("*", "anything at all"),
        Arguments.of("**", "ab"),
        Arguments.of("", ""),
        Arguments.of("a", "a"),
        Arguments.of("a*", "a"),
        Arguments.of("a*", "abc"),
        Arguments.of("*a", "a"),
        Arguments.of("*a", "bca"),
        Arguments.of("*a*b*", "xaybz"),
        Arguments.of("a?c", "abc"),
        Arguments.of("a?c", "a.c"),
        Arguments.of("?", "a"),
        Arguments.of("?", SMILEY),
        Arguments.of("*.bin", "en-pos.bin"),
        Arguments.of("*.bin", ".bin"),
        Arguments.of("*model.properties", "/x/opennlp-models-pos-en-1.2.0.jar!/model.properties"),
        Arguments.of("*opennlp-models-*", "/repo/opennlp-models-pos-en-1.2.0.jar"),
        Arguments.of("*opennlp-models-*.jar", "/repo/opennlp-models-pos-en-1.2.0.jar"),
        Arguments.of("*-en-*.jar", "/repo/opennlp-models-pos-en-1.2.0.jar"),
        Arguments.of("(a)", "(a)"),
        Arguments.of("[ab]", "[ab]"),
        Arguments.of("a$", "a$"),
        Arguments.of("a+", "a+"),
        Arguments.of("a\\b", "a\\b"),
        Arguments.of("*" + SMILEY + "*", "a" + SMILEY + "b"),
        Arguments.of(SMILEY + "?", SMILEY + SMILEY),
        // an unpaired surrogate is one character
        Arguments.of("?", "\uD83D"),
        Arguments.of("*", "\uD83D"),
        Arguments.of("a?c", "a\uDE00c"),
        Arguments.of("a*b*c", "abc"),
        Arguments.of("*?", "a"),
        Arguments.of("?*", "a"),
        Arguments.of("*\n*", "a\nb"),
        Arguments.of("a\nb", "a\nb"),
        Arguments.of("*\n*\n*", "a\nb\nc"),
        Arguments.of("*\r\n*", "a\r\nb"),
        // a wildcard covers a line terminator like any other character
        Arguments.of("*", "a\nb"),
        Arguments.of("*", "\n"),
        Arguments.of("*.bin", "a\n.bin"),
        Arguments.of("*a*b", "a\nab"),
        Arguments.of("?", "\n"),
        Arguments.of("a?b", "a\nb"),
        Arguments.of("*", "a\rb"),
        Arguments.of("*", "a\u0085b"),
        Arguments.of("*", "a\u2028b"),
        Arguments.of("*", "a\u2029b"),
        Arguments.of("**", ""),
        Arguments.of("***", "abc"),
        Arguments.of("a**b", "ab"),
        Arguments.of("a**b", "axyzb"),
        Arguments.of("*?*", "a"),
        Arguments.of("abc*", "abc"),
        Arguments.of("*.bin*", ".bin"),
        Arguments.of("[", "["),
        Arguments.of("[a-z]", "[a-z]"),
        Arguments.of("\\", "\\"),
        Arguments.of("\\*", "\\lib\\a.jar"),
        Arguments.of("\\Q*\\E", "\\Qx\\E"),
        // path separators are plain characters
        Arguments.of("*/models/*.bin", "/x/models/en.bin"),
        Arguments.of("/*.bin", "/en.bin"),
        Arguments.of("*/*", "a/"),
        Arguments.of("*/*", "/"),
        Arguments.of("*\\*", "C:\\lib\\a.jar"),
        // a wildcard covers the jar separator and the entry path
        Arguments.of("*.jar!/*.bin", "/repo/a.jar!/opennlp/en.bin"),
        Arguments.of("*.jar!/opennlp/*", "/repo/a.jar!/opennlp/"),
        Arguments.of("*!/*", "/repo/a.jar!/"),
        // a drive letter in the file part of a file URL
        Arguments.of("/C:/*.jar", "/C:/lib/a.jar"),
        Arguments.of("*:/lib/*", "/C:/lib/a.jar"),
        // percent-encoded file parts match as written
        Arguments.of("*%20*", "/my%20models/en.bin"),
        Arguments.of("/my%20models/*", "/my%20models/en.bin"),
        Arguments.of("?%20?", "a%20b"),
        Arguments.of("*" + SMILEY + "?", SMILEY + SMILEY));
  }

  @ParameterizedTest
  @MethodSource("accepted")
  void testMatchesAccepts(String wildcard, String input) {
    Assertions.assertTrue(WildcardMatcher.matches(wildcard, input),
        "wildcard '" + wildcard + "' should accept '" + input + "'");
  }

  private static Stream<Arguments> rejected() {
    return Stream.of(
        Arguments.of("a", ""),
        Arguments.of("", "a"),
        Arguments.of("a", "b"),
        Arguments.of("a", "ab"),
        Arguments.of("ab", "a"),
        Arguments.of("a*", "ba"),
        Arguments.of("*a", "ab"),
        Arguments.of("a?c", "ac"),
        Arguments.of("a?c", "abbc"),
        Arguments.of("?", ""),
        Arguments.of("?", "ab"),
        Arguments.of("*.bin", "en-pos.bini"),
        Arguments.of("*.bin", "en-posxbin"),
        Arguments.of("*.bin", "en-pos.BIN"),
        Arguments.of("*model.properties", "/x/model.properties.bak"),
        Arguments.of("*opennlp-models-*", "/repo/opennlp-model-pos-en-1.2.0.jar"),
        Arguments.of("(a)", "a"),
        Arguments.of("[ab]", "a"),
        Arguments.of("a+", "aa"),
        Arguments.of("a\\b", "a"),
        Arguments.of(SMILEY, "\uD83D"),
        Arguments.of("??", SMILEY),
        Arguments.of("*.BIN", "en-pos.bin"),
        Arguments.of("*?", ""),
        Arguments.of("?*", ""),
        Arguments.of("a\nb", "a b"),
        Arguments.of("a\nb", "a\rb"),
        Arguments.of("abcd", "abc"),
        Arguments.of("a?cd", "abc"),
        Arguments.of("*abcd", "abc"),
        Arguments.of("abc?", "abc"),
        Arguments.of("a**b", "a"),
        Arguments.of("a**b", "ba"),
        Arguments.of("*?*", ""),
        Arguments.of("[a-z]", "b"),
        Arguments.of("[", ""),
        Arguments.of("\\", "\\\\"),
        Arguments.of("\\Q*\\E", "x"),
        Arguments.of("a/b", "a\\b"),
        Arguments.of("a\\b", "a/b"),
        Arguments.of("/*.bin", "en.bin"),
        Arguments.of("*/models/*.bin", "/x/model/en.bin"),
        Arguments.of("*.jar!/en.bin", "/repo/a.jar!/models/en.bin"),
        Arguments.of("*.jar!/*", "/repo/a.jar/en.bin"),
        Arguments.of("/C:/*.jar", "/D:/lib/a.jar"),
        Arguments.of("C:/*.jar", "/C:/lib/a.jar"),
        Arguments.of("*my models*", "/my%20models/en.bin"),
        Arguments.of("*%20*", "/my models/en.bin"),
        Arguments.of("?%20?", "a b"),
        Arguments.of("?", "\r\n"),
        Arguments.of("*" + SMILEY + "?", SMILEY),
        Arguments.of(SMILEY + "*", "\uD83D"));
  }

  @ParameterizedTest
  @MethodSource("rejected")
  void testMatchesRejects(String wildcard, String input) {
    Assertions.assertFalse(WildcardMatcher.matches(wildcard, input),
        "wildcard '" + wildcard + "' should reject '" + input + "'");
  }

  private static Stream<Arguments> acceptRejectPairs() {
    return Stream.of(
        Arguments.of("*.bin", "en-pos.bin", "en-posxbin"),
        Arguments.of("*.bin", ".bin", "en-pos.bin.bak"),
        Arguments.of("a?c", "abc", "ac"),
        Arguments.of("a?c", "a\nc", "abbc"),
        Arguments.of("*a*b*", "xa\nyb\nz", "ba"),
        Arguments.of("(a)", "(a)", "a"),
        Arguments.of("[ab]", "[ab]", "a"),
        Arguments.of("a+", "a+", "aa"),
        Arguments.of("a$", "a$", "a"),
        Arguments.of("a\\b", "a\\b", "ab"),
        Arguments.of("\\Q*\\E", "\\Qx\\E", "x"),
        Arguments.of("[", "[", ""),
        Arguments.of("[a-z]", "[a-z]", "b"),
        Arguments.of("\\", "\\", "\\\\"),
        Arguments.of("a**", "a", "ba"),
        Arguments.of("*a", "\na", "\n"),
        Arguments.of("a*", "a\r\n", "\na"),
        Arguments.of("?", "\n", "\r\n"),
        Arguments.of("?", "\uD83D", SMILEY + SMILEY),
        Arguments.of("*.jar!/*.bin", "/repo/a.jar!/en.bin", "/repo/a.jar/en.bin"),
        Arguments.of("/C:/*", "/C:/lib/a.jar", "C:/lib/a.jar"),
        Arguments.of("*%20*", "/my%20models", "/my models"),
        Arguments.of(SMILEY + "?", SMILEY + SMILEY, SMILEY),
        Arguments.of("", "", "a"));
  }

  /** Checks literal characters and both wildcards against explicit accept/reject examples. */
  @ParameterizedTest
  @MethodSource("acceptRejectPairs")
  void testAcceptRejectPairs(String wildcard, String accepted, String rejected) {
    Assertions.assertTrue(WildcardMatcher.matches(wildcard, accepted));
    Assertions.assertFalse(WildcardMatcher.matches(wildcard, rejected));
  }

  private static Stream<Arguments> nullInputs() {
    return Stream.of(
        Arguments.of(null, "a"),
        Arguments.of("a", null),
        Arguments.of(null, null));
  }

  /**
   * Checks that a null wildcard or input fails fast instead of matching.
   */
  @ParameterizedTest
  @MethodSource("nullInputs")
  void testMatchesRejectsNull(String wildcard, String input) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> WildcardMatcher.matches(wildcard, input));
  }

  /**
   * Checks a wildcard that makes a backtracking regular expression take polynomial time,
   * {@code .*a.*a.*a.*b} against a long run of {@code a}. The matcher keeps one backtracking
   * point, so the rejection stays linear in the input for each {@code *}.
   */
  @Test
  void testPathologicalWildcardRejectsQuickly() {
    final String input = "a".repeat(100_000);
    Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5),
        () -> Assertions.assertFalse(WildcardMatcher.matches("*a*a*a*b", input)));
    Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5),
        () -> Assertions.assertTrue(WildcardMatcher.matches("*a*a*a*a", input)));
  }
}
