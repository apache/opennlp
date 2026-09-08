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
        Arguments.of("*\n*", "a\nb"),
        Arguments.of("a\nb", "a\nb"),
        Arguments.of("*\n*\n*", "a\nb\nc"),
        Arguments.of("*\r\n*", "a\r\nb"));
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
        // neither wildcard crosses a line terminator
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
  @MethodSource("rejected")
  void testMatchesRejects(String glob, String input) {
    Assertions.assertFalse(GlobMatcher.matches(glob, input),
        "glob '" + glob + "' should reject '" + input + "'");
  }

  @Test
  void testMatchesWildcardUsesFilePart() throws Exception {
    final AbstractClassPathModelFinder finder = new AbstractClassPathModelFinder() {
      @Override
      protected Object getContext() {
        return null;
      }

      @Override
      protected List<URI> getMatchingURIs(String wildcardPattern, Object context) {
        return List.of();
      }
    };
    final URL url = new URI(MODEL_URL).toURL();
    Assertions.assertTrue(finder.matchesWildcard(url, "*.bin"));
    Assertions.assertTrue(finder.matchesWildcard(url, "*opennlp-models-*"));
    Assertions.assertTrue(finder.matchesWildcard(url, "*en-pos.bin"));
    Assertions.assertFalse(finder.matchesWildcard(url, "en-pos.bin"));
    Assertions.assertFalse(finder.matchesWildcard(url, "*.properties"));
    Assertions.assertFalse(finder.matchesWildcard(url, "jar:*"));
  }
}
