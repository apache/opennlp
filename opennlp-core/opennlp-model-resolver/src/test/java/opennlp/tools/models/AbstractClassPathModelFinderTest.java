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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the wildcard API that {@link AbstractClassPathModelFinder} offers to subclasses.
 */
public class AbstractClassPathModelFinderTest {

  private static final String MODEL_URL =
      "jar:file:/repo/opennlp-models-pos-en-1.2.0.jar!/opennlp/models/en-pos.bin";

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

  private static Stream<Arguments> urlsAndWildcards() {
    return Stream.of(
        Arguments.of(MODEL_URL, "*.bin", true),
        Arguments.of(MODEL_URL, "*.jar!/opennlp/models/*.bin", true),
        Arguments.of(MODEL_URL, "*.jar!/*", true),
        Arguments.of(MODEL_URL, "*.jar!/en-pos.bin", false),
        Arguments.of(MODEL_URL, "*.jar", false),
        Arguments.of(MODEL_URL, "*opennlp-models-???-en-*", true),
        Arguments.of(MODEL_URL, "*opennlp-models-??-en-*", false),
        // the file part of a file URL starts with a slash, the drive letter follows
        Arguments.of("file:/C:/lib/opennlp-models-pos-en-1.2.0.jar", "/C:/*.jar", true),
        Arguments.of("file:/C:/lib/opennlp-models-pos-en-1.2.0.jar", "C:/*.jar", false),
        Arguments.of("file:/C:/lib/opennlp-models-pos-en-1.2.0.jar", "*opennlp-models-*", true),
        // the file part of a jar URL is the inner URL, scheme included
        Arguments.of("jar:file:/C:/lib/a.jar!/opennlp/en-pos.bin", "file:/C:/*.jar!/*.bin", true),
        Arguments.of("jar:file:/C:/lib/a.jar!/opennlp/en-pos.bin", "/C:/*.jar!/*.bin", false),
        Arguments.of(MODEL_URL, "file:/repo/*.jar!/opennlp/models/en-pos.bin", true),
        Arguments.of(MODEL_URL, "/repo/*.jar!/opennlp/models/en-pos.bin", false),
        Arguments.of("jar:file:/C:/lib/a.jar!/opennlp/en-pos.bin", "*/en-pos.bin", true),
        Arguments.of("jar:file:/C:/lib/a.jar!/opennlp/en-pos.bin", "*\\en-pos.bin", false),
        // URI escapes are decoded once, while literal plus signs are preserved
        Arguments.of("file:/my%20models/en-pos.bin", "*%20*", false),
        Arguments.of("file:/my%20models/en-pos.bin", "*my models*", true),
        Arguments.of("file:/my%20models/en-pos.bin", "/my models/*.bin", true),
        Arguments.of("file:/models/model-%F0%9F%98%80.jar", "*model-?.jar", true),
        Arguments.of("file:/models/model-%F0%9F%98%80.jar", "*model-??.jar", false),
        Arguments.of("file:/models/model-%2520.jar", "*model-%20.jar", true),
        Arguments.of("file:/models/model-%2520.jar", "*model- .jar", false),
        Arguments.of("file:/models/model+1.jar", "*model+1.jar", true),
        Arguments.of("file:/models/model+1.jar", "*model 1.jar", false),
        Arguments.of("jar:file:/my%20models/a.jar!/caf%C3%A9/%F0%9F%98%80.bin",
            "file:/my models/a.jar!/café/?.bin", true),
        Arguments.of("jar:file:/models/a.jar!/model%2520.bin", "*model%20.bin", true),
        Arguments.of("jar:file:/models/a.jar!/model%2520.bin", "*model .bin", false),
        Arguments.of("jar:file:/models/a.jar!/model%23x.bin", "*model#x.bin", true),
        Arguments.of("jar:file:/models/a.jar!/model%3Fx.bin", "*model?x.bin", true),
        // a query is part of the file part, a fragment is not
        Arguments.of("http://host/models/en-pos.bin?x=1", "*.bin", false),
        Arguments.of("http://host/models/en-pos.bin?x=1", "*.bin?x=1", true),
        Arguments.of("http://host/models/en-pos.bin#top", "*.bin", true),
        Arguments.of("file:/models/en-pos.bin", "*", true),
        Arguments.of("file:/models/en-pos.bin", "", false),
        Arguments.of("file:/models/en-pos.bin", "?", false));
  }

  /**
   * Checks decoded file part matching against jar, file, and http URLs.
   */
  @ParameterizedTest
  @MethodSource("urlsAndWildcards")
  void testMatchesWildcardOnUrlFilePart(String url, String wildcard, boolean expected)
      throws Exception {
    final AbstractClassPathModelFinder finder = newProbeFinder();
    final URL parsed = new URI(url).toURL();
    Assertions.assertEquals(expected, finder.matchesWildcard(parsed, wildcard),
        "wildcard '" + wildcard + "' on '" + parsed.getFile() + "'");
  }

  /**
   * An example finder using the supported wildcard API.
   */
  private static final class WildcardFinder extends AbstractClassPathModelFinder {

    private final List<URL> candidates;

    WildcardFinder(List<URL> candidates) {
      this.candidates = candidates;
    }

    @Override
    protected Object getContext() {
      return null;
    }

    @Override
    protected List<URI> getMatchingURIs(String wildcardPattern, Object context) {
      final List<URI> matches = new ArrayList<>();
      for (URL candidate : candidates) {
        if (matchesWildcard(candidate, "*" + wildcardPattern)) {
          try {
            matches.add(candidate.toURI());
          } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
          }
        }
      }
      return matches;
    }
  }

  /**
   * Checks that a custom finder filters through the supported wildcard API.
   */
  @Test
  void testCustomSubclassFilters() throws Exception {
    final URL bin = new URI(MODEL_URL).toURL();
    final URL properties = new URI(
        "jar:file:/repo/opennlp-models-pos-en-1.2.0.jar!/opennlp/models/model.properties").toURL();
    final URL other = new URI("jar:file:/repo/other.jar!/x/readme.txt").toURL();
    final WildcardFinder finder = new WildcardFinder(List.of(bin, properties, other));
    Assertions.assertEquals(List.of(bin.toURI()), finder.getMatchingURIs("*.bin", null));
    Assertions.assertEquals(List.of(properties.toURI()),
        finder.getMatchingURIs("model.properties", null));
    Assertions.assertEquals(List.of(bin.toURI(), properties.toURI()),
        finder.getMatchingURIs("opennlp-models-*", null));
    Assertions.assertEquals(List.of(), finder.getMatchingURIs("(x)", null));
  }

  /**
   * Checks that null arguments to the finder matchers fail fast.
   */
  @Test
  void testFinderMatchersRejectNull() throws Exception {
    final AbstractClassPathModelFinder finder = newProbeFinder();
    final URL url = new URI(MODEL_URL).toURL();
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> finder.matchesWildcard(null, "*.bin"));
    Assertions.assertThrows(IllegalArgumentException.class, () -> finder.matchesWildcard(url, null));
  }

  private static Stream<Arguments> unescapedUrls() {
    return Stream.of(
        Arguments.of("/C:/my models/opennlp-models-pos-en-1.2.0.jar", "*opennlp-models-*", true),
        Arguments.of("/C:/my models/opennlp-models-pos-en-1.2.0.jar", "*/my models/*.jar", true),
        Arguments.of("/C:/my models/opennlp-models-pos-en-1.2.0.jar", "*.bin", false),
        Arguments.of("/models/a b%20c.jar", "*a b%20c.jar", true),
        Arguments.of("/models/a b%20c.jar", "*a b c.jar", false));
  }

  /**
   * Checks that a URL that is not a valid URI is matched on its raw file part instead of failing.
   */
  @ParameterizedTest
  @MethodSource("unescapedUrls")
  void testMatchesWildcardOnUrlThatIsNotAUri(String path, String wildcard, boolean expected)
      throws MalformedURLException {
    final URL parsed = UnescapedUrls.of(path);
    Assertions.assertThrows(URISyntaxException.class, parsed::toURI);
    Assertions.assertEquals(expected, newProbeFinder().matchesWildcard(parsed, wildcard));
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

  /**
   * Checks that a null jar prefix fails like the other finder arguments.
   */
  @Test
  void testConstructorRejectsNullPrefix() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new AbstractClassPathModelFinder(null) {
      @Override
      protected Object getContext() {
        return null;
      }

      @Override
      protected List<URI> getMatchingURIs(String wildcardPattern, Object context) {
        return List.of();
      }
    });
  }
}
