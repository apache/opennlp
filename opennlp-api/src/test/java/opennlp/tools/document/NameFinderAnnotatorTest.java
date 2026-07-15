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

package opennlp.tools.document;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests that {@link NameFinderAnnotator} maps token-index mentions to character spans on
 * the original text and clears the finder's adaptive data per document.
 */
public class NameFinderAnnotatorTest {

  @Test
  void testTokenIndexSpansBecomeCharacterSpans() {
    final AtomicInteger cleared = new AtomicInteger();
    final TokenNameFinder finder = new TokenNameFinder() {

      @Override
      public Span[] find(String[] tokens) {
        // "New York" as a two-token person-free location mention
        return new Span[] {new Span(1, 3, "location")};
      }

      @Override
      public void clearAdaptiveData() {
        cleared.incrementAndGet();
      }
    };

    final Document document = Document.of("in New York today")
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 2), "in"),
            new Annotation<>(new Span(3, 6), "New"),
            new Annotation<>(new Span(7, 11), "York"),
            new Annotation<>(new Span(12, 17), "today")));

    final Document annotated = new NameFinderAnnotator(finder).annotate(document);
    final List<Annotation<String>> entities = annotated.get(Layers.ENTITIES);
    assertEquals(1, entities.size());
    assertEquals(new Span(3, 11, "location"), entities.get(0).span());
    assertEquals("location", entities.get(0).value());
    assertEquals("New York",
        entities.get(0).span().getCoveredText(annotated.text()).toString());
    assertEquals(1, cleared.get());
  }

  @Test
  void testMissingTokenLayerThrows() {
    final TokenNameFinder finder = new TokenNameFinder() {

      @Override
      public Span[] find(String[] tokens) {
        return new Span[0];
      }

      @Override
      public void clearAdaptiveData() {
      }
    };
    assertThrows(IllegalArgumentException.class,
        () -> new NameFinderAnnotator(finder).annotate(Document.of("no tokens")));
  }
}
