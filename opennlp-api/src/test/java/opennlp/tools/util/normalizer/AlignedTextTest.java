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
package opennlp.tools.util.normalizer;

import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AlignedTextTest {

  private static void assertSpan(int start, int end, Span span) {
    assertEquals(start, span.getStart(), "start");
    assertEquals(end, span.getEnd(), "end");
  }

  @Test
  void originalPreservesCallerCharSequence() {
    final StringBuilder original = new StringBuilder("a  b");
    final Alignment alignment = new Alignment.Builder().equal(1).replace(2, 1).equal(1).build(4);
    final AlignedText aligned = new AlignedText(original, "a b", alignment);
    assertSame(original, aligned.original());
  }

  @Test
  void normalizedIsCharSequenceAndNormalizedStringMatchesContent() {
    final Alignment alignment = new Alignment.Builder().equal(1).replace(2, 1).equal(1).build(4);
    final AlignedText aligned = new AlignedText("a  b", "a b", alignment);
    assertEquals("a b", aligned.normalized().toString());
    assertEquals("a b", aligned.normalizedString());
  }

  @Test
  void normalizedStringReturnsSameInstanceWhenNormalizedIsString() {
    final String normalized = "a b";
    final Alignment alignment = new Alignment.Builder().equal(1).replace(2, 1).equal(1).build(4);
    final AlignedText aligned = new AlignedText("a  b", normalized, alignment);
    assertSame(normalized, aligned.normalized());
    assertSame(normalized, aligned.normalizedString());
  }

  @Test
  void normalizedStringMaterializesNonStringCharSequence() {
    final StringBuilder normalized = new StringBuilder("a b");
    final Alignment alignment = new Alignment.Builder().equal(1).replace(2, 1).equal(1).build(4);
    final AlignedText aligned = new AlignedText("a  b", normalized, alignment);
    assertSame(normalized, aligned.normalized());
    assertEquals("a b", aligned.normalizedString());
  }

  @Test
  void toOriginalSpanDelegatesToAlignment() {
    // "a  b" -> "a b"
    final Alignment alignment = new Alignment.Builder().equal(1).replace(2, 1).equal(1).build(4);
    final AlignedText aligned = new AlignedText("a  b", "a b", alignment);
    assertSpan(1, 3, aligned.toOriginalSpan(1, 2)); // collapsed space covers both originals
    assertSpan(3, 4, aligned.toOriginalSpan(2, 3)); // "b"
  }

  @Test
  void toNormalizedSpanDelegatesToAlignment() {
    // "a b c" -> "abc"
    final Alignment alignment = new Alignment.Builder()
        .equal(1).replace(1, 0).equal(1).replace(1, 0).equal(1).build(5);
    final AlignedText aligned = new AlignedText("a b c", "abc", alignment);
    assertSpan(1, 2, aligned.toNormalizedSpan(2, 3));
  }

  @Test
  void collapseAlignedProducesStringBackedNormalizedForm() {
    final AlignedText aligned = CharClass.whitespace().collapseAligned("a  b");
    assertEquals("a b", aligned.normalizedString());
    assertEquals("a b", aligned.normalized().toString());
    assertSpan(1, 3, aligned.toOriginalSpan(1, 2));
  }

}
