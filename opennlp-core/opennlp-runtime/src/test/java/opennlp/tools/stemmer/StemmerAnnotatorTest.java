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

import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StemmerAnnotatorTest {

  @Test
  void testStemsAlignWithTokens() {
    final Document document = Document.of("running dogs")
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 7), "running"),
            new Annotation<>(new Span(8, 12), "dogs")));

    final Document stemmed = new StemmerAnnotator(
        new PorterStemmer()).annotate(document);

    final List<Annotation<String>> stems = stemmed.get(StemmerAnnotator.STEMS);
    assertEquals(2, stems.size());
    assertEquals("run", stems.get(0).value());
    assertEquals(new Span(0, 7), stems.get(0).span());
    assertEquals("dog", stems.get(1).value());
  }

  @Test
  void testInvalidArguments() {
    assertThrows(IllegalArgumentException.class,
        () -> new StemmerAnnotator(null));
    final StemmerAnnotator annotator = new StemmerAnnotator(new PorterStemmer());
    assertThrows(IllegalArgumentException.class, () -> annotator.annotate(null));
  }

  /**
   * Verifies that a document without a token layer is rejected with a message naming the
   * missing layer, instead of silently producing an empty stem layer.
   */
  @Test
  void testAbsentTokenLayerThrowsWithExactMessage() {
    final StemmerAnnotator annotator = new StemmerAnnotator(new PorterStemmer());
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(Document.of("no tokens")));
    assertEquals("document lacks the required layer opennlp:tokens<String>", e.getMessage());
  }

  /**
   * Verifies that a present-but-empty token layer yields a present-but-empty stem layer
   * rather than an exception.
   */
  @Test
  void testEmptyPresentTokenLayerYieldsEmptyStemLayer() {
    final Document document = Document.of("").with(Layers.TOKENS, List.of());
    final Document stemmed = new StemmerAnnotator(new PorterStemmer()).annotate(document);
    assertTrue(stemmed.layers().contains(StemmerAnnotator.STEMS));
    assertTrue(stemmed.get(StemmerAnnotator.STEMS).isEmpty());
  }
}
