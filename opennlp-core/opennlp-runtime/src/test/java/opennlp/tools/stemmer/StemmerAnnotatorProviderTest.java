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
package opennlp.tools.stemmer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnnotator;
import opennlp.tools.document.DocumentAnnotatorProvider;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;
import opennlp.tools.util.ext.ProviderSpec;
import opennlp.tools.util.ext.Providers;
import opennlp.tools.util.ext.UnsatisfiedProviderException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StemmerAnnotatorProviderTest {

  private final StemmerAnnotatorProvider provider = new StemmerAnnotatorProvider();

  @Test
  void testName() {
    assertEquals("stemmer", provider.name(), "provider name");
  }

  @ParameterizedTest
  @ValueSource(strings = {"porter", "PORTER", "english", "English", "GERMAN", "portuguese"})
  void testSupportsKnownAlgorithm(final String algorithm) {
    assertTrue(provider.supports(algorithm(algorithm)), algorithm);
  }

  @Test
  void testSupportsAbsentAlgorithm() {
    assertTrue(provider.supports(ProviderSpec.empty()), "an absent algorithm means porter");
  }

  @Test
  void testRejectsOtherSpecs() {
    assertFalse(provider.supports(algorithm("klingon")), "an unknown algorithm");
    assertFalse(provider.supports(algorithm(" ")), "a blank algorithm");
    assertFalse(provider.supports(ProviderSpec.of(Map.of("language", "en"))), "another option");
    assertFalse(provider.supports(ProviderSpec.of(Map.of("Algorithm", "english"))),
        "option names are case-sensitive");
    assertFalse(provider.supports(ProviderSpec.of(Path.of("model.bin"))), "a location");
    assertThrows(IllegalArgumentException.class, () -> provider.supports(null), "null spec");
    assertThrows(IllegalArgumentException.class, () -> provider.create(null), "null spec");
    assertThrows(IllegalArgumentException.class, () -> provider.create(algorithm("klingon")),
        "an unsupported spec");
  }

  /** {@code porter} always means the Porter stemmer, not the Snowball algorithm of that name. */
  @Test
  void testPorterIsNotTheSnowballAlgorithm() {
    final DocumentAnnotator porter = provider.create(algorithm("PORTER"));
    final Document document = Document.of("generously").with(Layers.TOKENS,
        List.of(new Annotation<>(new Span(0, 10), "generously")));
    assertEquals("gener", porter.annotate(document).get(StemmerAnnotator.STEMS).get(0).value(),
        "the Porter stemmer");
    final DocumentAnnotator snowball = provider.create(algorithm("english"));
    assertEquals("generous", snowball.annotate(document).get(StemmerAnnotator.STEMS).get(0).value(),
        "the Snowball English stemmer");
  }

  @ParameterizedTest
  @ValueSource(strings = {"porter", "english"})
  void testCreatesStemmingAnnotator(final String algorithm) {
    final Document document = Document.of("running dogs").with(Layers.TOKENS, List.of(
        new Annotation<>(new Span(0, 7), "running"), new Annotation<>(new Span(8, 12), "dogs")));
    final ProviderSpec spec = algorithm(algorithm);
    final DocumentAnnotator annotator = provider.create(spec);
    assertInstanceOf(StemmerAnnotator.class, annotator, "annotator");
    assertNotSame(annotator, provider.create(spec), "each call creates its own annotator");
    final List<Annotation<String>> stems = annotator.annotate(document).get(StemmerAnnotator.STEMS);
    assertEquals(List.of("run", "dog"), List.of(stems.get(0).value(), stems.get(1).value()),
        algorithm);
  }

  @Test
  void testRegistered() {
    final Providers<DocumentAnnotatorProvider> providers =
        Providers.of(DocumentAnnotatorProvider.class);
    assertInstanceOf(StemmerAnnotatorProvider.class,
        providers.byName(StemmerAnnotatorProvider.NAME).orElseThrow(), "registered by name");
    assertInstanceOf(StemmerAnnotatorProvider.class, providers.select(algorithm("english")),
        "selected for a stemmer request");
    assertTrue(providers.supporting(ProviderSpec.of(Path.of("model.bin"))).isEmpty(),
        "a model file is not a stemmer request");
  }

  @Test
  void testDisabledThroughTheConfiguration() {
    final Map<String, String> configuration =
        Map.of(Providers.CONFIGURATION_KEY_PREFIX + DocumentAnnotatorProvider.class.getName()
            + Providers.DISABLED_KEY_SUFFIX, StemmerAnnotatorProvider.NAME);
    final Providers<DocumentAnnotatorProvider> providers = Providers.of(
        DocumentAnnotatorProvider.class, getClass().getClassLoader(), configuration::get);
    assertTrue(providers.byName(StemmerAnnotatorProvider.NAME).isEmpty(), "disabled by name");
    assertThrows(UnsatisfiedProviderException.class, () -> providers.select(algorithm("english")),
        "no provider remains");
  }

  /**
   * @param algorithm The algorithm option value.
   * @return A spec with only the algorithm option.
   */
  private ProviderSpec algorithm(final String algorithm) {
    return ProviderSpec.of(Map.of(StemmerAnnotatorProvider.ALGORITHM_OPTION, algorithm));
  }
}
