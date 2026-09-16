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
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;
import opennlp.tools.util.ext.ComponentProvider;
import opennlp.tools.util.ext.ComponentProviders;
import opennlp.tools.util.ext.ComponentSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stemmer annotator as a registered document annotator component. */
class StemmerAnnotatorProviderTest {

  private final StemmerAnnotatorProvider provider = new StemmerAnnotatorProvider();

  @Test
  void testIdentity() {
    assertEquals(DocumentAnnotator.class, provider.type());
    assertEquals("stemmer", provider.name());
    assertEquals(0, provider.priority());
    assertTrue(provider.isAvailable());
  }

  @ParameterizedTest
  @ValueSource(strings = {"porter", "PORTER", "english", "English", "GERMAN", "portuguese"})
  void testSupportsKnownAlgorithms(String algorithm) {
    assertTrue(provider.supports(ComponentSpec.of(Map.of("algorithm", algorithm))));
  }

  @Test
  void testSupportsAbsentAlgorithmAndRejectsOtherRequests() {
    assertTrue(provider.supports(ComponentSpec.empty()));
    assertFalse(provider.supports(ComponentSpec.of(Map.of("algorithm", "klingon"))));
    assertFalse(provider.supports(ComponentSpec.of(Map.of("algorithm", " "))));
    assertFalse(provider.supports(ComponentSpec.of(Map.of("language", "en"))));
    assertFalse(provider.supports(ComponentSpec.of(Path.of("model.bin"))));
    assertFalse(provider.supports(null));
  }

  @Test
  void testCreatesAnnotatorsThatStemTokens() {
    Document document = Document.of("running dogs").with(Layers.TOKENS, List.of(
        new Annotation<>(new Span(0, 7), "running"), new Annotation<>(new Span(8, 12), "dogs")));
    for (String algorithm : new String[] {"porter", "english"}) {
      DocumentAnnotator annotator = provider.create(ComponentSpec.of(Map.of("algorithm", algorithm)));
      assertInstanceOf(StemmerAnnotator.class, annotator);
      List<Annotation<String>> stems = annotator.annotate(document).get(StemmerAnnotator.STEMS);
      assertEquals(List.of("run", "dog"), List.of(stems.get(0).value(), stems.get(1).value()),
          algorithm);
    }
    assertTrue(provider.create(ComponentSpec.empty()) != provider.create(ComponentSpec.empty()));
    assertThrows(IllegalArgumentException.class, () -> provider.create(null));
    assertThrows(IllegalArgumentException.class,
        () -> provider.create(ComponentSpec.of(Map.of("algorithm", "klingon"))));
  }

  /** The provider is registered in this module and is selected for a stemmer request. */
  @Test
  void testRegisteredAndSelected() {
    ComponentSpec spec = ComponentSpec.of(Map.of("algorithm", "english"));
    ComponentProvider<DocumentAnnotator> selected =
        ComponentProviders.select(DocumentAnnotator.class, spec);
    assertInstanceOf(StemmerAnnotatorProvider.class, selected);
    assertInstanceOf(StemmerAnnotatorProvider.class,
        ComponentProviders.get(DocumentAnnotator.class, "stemmer"));
    assertTrue(ComponentProviders.supporting(DocumentAnnotator.class,
        ComponentSpec.of(Path.of("model.bin"))).isEmpty());
  }
}
