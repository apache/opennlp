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
import java.util.Set;

import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link Document} container: typed layer access, copy-on-add immutability,
 * and the insertion-time validation that protects the layer invariants.
 */
public class DocumentTest {

  private static final LayerKey<String> WORDS = LayerKey.of("words", String.class);
  private static final LayerKey<Integer> NUMBERS = LayerKey.of("numbers", Integer.class);

  @Test
  void testEmptyDocument() {
    final Document document = Document.of("the dog");
    assertEquals("the dog", document.text());
    assertTrue(document.layers().isEmpty());
    assertTrue(document.get(WORDS).isEmpty());
  }

  @Test
  void testWithAddsATypedLayer() {
    final Document document = Document.of("the dog")
        .with(WORDS, List.of(new Annotation<>(new Span(0, 3), "the"),
            new Annotation<>(new Span(4, 7), "dog")));
    assertEquals(Set.of(WORDS), document.layers());
    final List<Annotation<String>> words = document.get(WORDS);
    assertEquals(2, words.size());
    assertEquals("dog", words.get(1).value());
    assertEquals(new Span(4, 7), words.get(1).span());
  }

  @Test
  void testWithIsCopyOnAdd() {
    final Document empty = Document.of("42");
    final Document grown = empty.with(NUMBERS,
        List.of(new Annotation<>(new Span(0, 2), 42)));
    assertTrue(empty.layers().isEmpty());
    assertEquals(Set.of(NUMBERS), grown.layers());
    // unchanged layers are shared, not copied
    final Document both = grown.with(WORDS, List.of());
    assertSame(grown.get(NUMBERS), both.get(NUMBERS));
  }

  @Test
  void testEqualKeysFromDifferentConstantsInteroperate() {
    final Document document = Document.of("the")
        .with(LayerKey.of("words", String.class),
            List.of(new Annotation<>(new Span(0, 3), "the")));
    assertEquals(1, document.get(WORDS).size());
    assertNotEquals(WORDS, LayerKey.of("words", CharSequence.class));
  }

  @Test
  void testDuplicateLayerThrows() {
    final Document document = Document.of("the").with(WORDS, List.of());
    assertThrows(IllegalArgumentException.class, () -> document.with(WORDS, List.of()));
  }

  @Test
  void testSpanBeyondTextThrows() {
    assertThrows(IllegalArgumentException.class, () -> Document.of("the")
        .with(WORDS, List.of(new Annotation<>(new Span(0, 4), "the?"))));
  }

  @Test
  void testValueTypeIsCheckedOnInsertion() {
    // a raw-typed caller cannot smuggle a mismatched value past the layer type
    @SuppressWarnings({"unchecked", "rawtypes"})
    final LayerKey<Object> raw = (LayerKey) NUMBERS;
    assertThrows(IllegalArgumentException.class, () -> Document.of("the")
        .with(raw, List.of(new Annotation<>(new Span(0, 3), "not a number"))));
  }

  @Test
  void testNullArgumentsThrow() {
    final Document document = Document.of("the");
    assertThrows(IllegalArgumentException.class, () -> Document.of(null));
    assertThrows(IllegalArgumentException.class, () -> document.get(null));
    assertThrows(IllegalArgumentException.class, () -> document.with(null, List.of()));
    assertThrows(IllegalArgumentException.class, () -> document.with(WORDS, null));
  }

  @Test
  void testAnnotationValidation() {
    assertThrows(IllegalArgumentException.class, () -> new Annotation<>(null, "the"));
    assertThrows(IllegalArgumentException.class, () -> new Annotation<>(new Span(0, 3), null));
  }

  @Test
  void testLayerKeyValidation() {
    assertThrows(IllegalArgumentException.class, () -> LayerKey.of(" ", String.class));
    assertThrows(IllegalArgumentException.class, () -> LayerKey.of(null, String.class));
    assertThrows(IllegalArgumentException.class, () -> LayerKey.of("words", null));
  }
}
