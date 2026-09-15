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

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class GlobMatcherTest {

  private static final String MODEL_URL =
      "jar:file:/repo/opennlp-models-pos-en-1.2.0.jar!/opennlp/models/en-pos.bin";

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
        Arguments.of("*", "a\u2029b"));
  }

  @ParameterizedTest
  @MethodSource("accepted")
  void testMatchesAccepts(String glob, String input) {
    Assertions.assertTrue(GlobMatcher.matches(glob, input),
        "glob '" + glob + "' should accept '" + input + "'");
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
        Arguments.of("a\nb", "a\rb"));
  }

  @ParameterizedTest
  @MethodSource("rejected")
  void testMatchesRejects(String glob, String input) {
    Assertions.assertFalse(GlobMatcher.matches(glob, input),
        "glob '" + glob + "' should reject '" + input + "'");
  }

  /**
   * Creates a finder probe with no context and no matches.
   *
   * @return A minimal {@link AbstractClassPathModelFinder} for matcher tests.
   */
  AbstractClassPathModelFinder newProbeFinder() {
    return new AbstractClassPathModelFinder() {
      @Override
      protected Object getContext() {
        return null;
      }

      @Override
      protected List<URI> getMatchingURIs(String wildcardPattern, Object context) {
        return List.of();
      }
    };
  }

  private static Stream<Arguments> asRegexGlobs() {
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
        Arguments.of(SMILEY + "?", SMILEY + SMILEY, SMILEY),
        Arguments.of("", "", "a"));
  }

  /**
   * Checks that the deprecated translation yields a regular expression with the meaning of
   * the glob: every character other than the two wildcards stands for itself.
   */
  @ParameterizedTest
  @MethodSource("asRegexGlobs")
  void testAsRegexTranslatesGlob(String glob, String accepted, String rejected) {
    final Pattern regex = Pattern.compile(newProbeFinder().asRegex(glob));
    Assertions.assertTrue(regex.matcher(accepted).matches(),
        "regex '" + regex + "' should accept '" + accepted + "'");
    Assertions.assertFalse(regex.matcher(rejected).matches(),
        "regex '" + regex + "' should reject '" + rejected + "'");
    Assertions.assertEquals(GlobMatcher.matches(glob, accepted), regex.matcher(accepted).matches());
    Assertions.assertEquals(GlobMatcher.matches(glob, rejected), regex.matcher(rejected).matches());
  }

  /**
   * Checks that the deprecated matcher evaluates the pattern as the regular expression it is.
   */
  @Test
  void testMatchesPatternEvaluatesRegex() throws Exception {
    final AbstractClassPathModelFinder finder = newProbeFinder();
    final URL url = new URI(MODEL_URL).toURL();
    Assertions.assertTrue(finder.matchesPattern(url, Pattern.compile(".*\\.bin")));
    Assertions.assertTrue(finder.matchesPattern(url, Pattern.compile(".*opennlp-models-[a-z]+-en-.*")));
    Assertions.assertTrue(finder.matchesPattern(url, Pattern.compile(finder.asRegex("*.bin"))));
    // the whole file part must match, as before
    Assertions.assertFalse(finder.matchesPattern(url, Pattern.compile("en-pos\\.bin")));
    Assertions.assertFalse(finder.matchesPattern(url, Pattern.compile(".*\\.properties")));
    // a literal pattern is not read as a glob
    Assertions.assertFalse(finder.matchesPattern(url, Pattern.compile("*.bin", Pattern.LITERAL)));
  }

  /**
   * A finder written against the previous API: it translates the glob with
   * {@code asRegex} and filters with {@code matchesPattern}.
   */
  private static final class LegacyFinder extends AbstractClassPathModelFinder {

    private final List<URL> candidates;

    LegacyFinder(List<URL> candidates) {
      this.candidates = candidates;
    }

    @Override
    protected Object getContext() {
      return null;
    }

    @Override
    @SuppressWarnings("removal")
    protected List<URI> getMatchingURIs(String wildcardPattern, Object context) {
      final Pattern pattern = Pattern.compile(asRegex("*" + wildcardPattern));
      final List<URI> matches = new java.util.ArrayList<>();
      for (URL candidate : candidates) {
        if (matchesPattern(candidate, pattern)) {
          try {
            matches.add(candidate.toURI());
          } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
          }
        }
      }
      return matches;
    }
  }

  /**
   * Checks that a subclass compiled against the previous API still filters correctly.
   */
  @Test
  void testLegacySubclassStillFilters() throws Exception {
    final URL bin = new URI(MODEL_URL).toURL();
    final URL properties = new URI(
        "jar:file:/repo/opennlp-models-pos-en-1.2.0.jar!/opennlp/models/model.properties").toURL();
    final URL other = new URI("jar:file:/repo/other.jar!/x/readme.txt").toURL();
    final LegacyFinder finder = new LegacyFinder(List.of(bin, properties, other));
    Assertions.assertEquals(List.of(bin.toURI()), finder.getMatchingURIs("*.bin", null));
    Assertions.assertEquals(List.of(properties.toURI()),
        finder.getMatchingURIs("model.properties", null));
    Assertions.assertEquals(List.of(bin.toURI(), properties.toURI()),
        finder.getMatchingURIs("opennlp-models-*", null));
    Assertions.assertEquals(List.of(), finder.getMatchingURIs("(x)", null));
  }

  private static Stream<Arguments> nullInputs() {
    return Stream.of(
        Arguments.of(null, "a"),
        Arguments.of("a", null),
        Arguments.of(null, null));
  }

  /**
   * Checks that null glob or input fails fast instead of matching.
   */
  @ParameterizedTest
  @MethodSource("nullInputs")
  void testMatchesRejectsNull(String glob, String input) {
    Assertions.assertThrows(IllegalArgumentException.class, () -> GlobMatcher.matches(glob, input));
  }

  /**
   * Checks that null arguments to the finder matchers fail fast.
   */
  @Test
  void testFinderMatchersRejectNull() throws Exception {
    final AbstractClassPathModelFinder finder = newProbeFinder();
    final URL url = new URI(MODEL_URL).toURL();
    Assertions.assertThrows(IllegalArgumentException.class, () -> finder.asRegex(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> finder.matchesWildcard(null, "*.bin"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> finder.matchesWildcard(url, null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> finder.matchesPattern(null, Pattern.compile(".*\\.bin")));
    Assertions.assertThrows(IllegalArgumentException.class, () -> finder.matchesPattern(url, null));
  }

  @Test
  void testMatchesWildcardUsesFilePart() throws Exception {
    final AbstractClassPathModelFinder finder = newProbeFinder();
    final URL url = new URI(MODEL_URL).toURL();
    Assertions.assertTrue(finder.matchesWildcard(url, "*.bin"));
    Assertions.assertTrue(finder.matchesWildcard(url, "*opennlp-models-*"));
    Assertions.assertTrue(finder.matchesWildcard(url, "*en-pos.bin"));
    Assertions.assertFalse(finder.matchesWildcard(url, "en-pos.bin"));
    Assertions.assertFalse(finder.matchesWildcard(url, "*.properties"));
    Assertions.assertFalse(finder.matchesWildcard(url, "jar:*"));
  }
}
