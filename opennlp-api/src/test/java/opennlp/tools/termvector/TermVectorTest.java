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

package opennlp.tools.termvector;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link TermVector} invariants: the two legal shapes (full with one span
 * per occurrence, scoring-only with none), the validation of everything in between, and
 * the immutability of the span list.
 */
public class TermVectorTest {

  @Test
  void testWithSpansDerivesTheFrequency() {
    final TermVector vector =
        TermVector.withSpans("dog", List.of(new Span(4, 7), new Span(19, 22)));
    assertEquals("dog", vector.term());
    assertEquals(2, vector.frequency());
    assertEquals(List.of(new Span(4, 7), new Span(19, 22)), vector.spans());
  }

  @Test
  void testCountCarriesNoSpans() {
    final TermVector vector = TermVector.count("dog", 3);
    assertEquals("dog", vector.term());
    assertEquals(3, vector.frequency());
    assertTrue(vector.spans().isEmpty());
  }

  @Test
  void testSpanListIsDetachedFromTheCallersInput() {
    final List<Span> spans = new ArrayList<>(List.of(new Span(0, 3)));
    final TermVector vector = TermVector.withSpans("the", spans);
    spans.add(new Span(4, 7));
    assertEquals(1, vector.spans().size());
    assertThrows(UnsupportedOperationException.class,
        () -> vector.spans().add(new Span(8, 11)));
  }

  @Test
  void testNullTermIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new TermVector(null, 1, List.of(new Span(0, 1))));
  }

  @Test
  void testNullSpanListIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new TermVector("dog", 1, null));
  }

  @Test
  void testZeroFrequencyIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> TermVector.count("dog", 0));
  }

  @Test
  void testEmptySpanListCannotDeriveAFrequency() {
    assertThrows(IllegalArgumentException.class,
        () -> TermVector.withSpans("dog", List.of()));
  }

  @Test
  void testPartialSpanListIsRejected() {
    // Two occurrences but only one recorded span: neither full nor scoring-only.
    assertThrows(IllegalArgumentException.class,
        () -> new TermVector("dog", 2, List.of(new Span(0, 3))));
  }
}
