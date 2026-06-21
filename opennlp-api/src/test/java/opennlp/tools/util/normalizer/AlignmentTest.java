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
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AlignmentTest {

  private static void assertSpan(int start, int end, Span span) {
    assertEquals(start, span.getStart(), "start");
    assertEquals(end, span.getEnd(), "end");
  }

  @Test
  void testIdentityMapsOneToOne() {
    final Alignment a = new Alignment.Builder().equal(3).build(3); // "abc" unchanged
    assertEquals(3, a.normalizedLength());
    assertEquals(3, a.originalLength());
    assertSpan(0, 3, a.toOriginalSpan(0, 3));
    assertSpan(1, 2, a.toOriginalSpan(1, 2));
  }

  @Test
  void testCollapsedRunMapsToWholeRun() {
    // "ab  " -> "ab " : keep "ab", collapse two spaces into one.
    final Alignment a = new Alignment.Builder().equal(2).replace(2, 1).build(4);
    assertSpan(0, 2, a.toOriginalSpan(0, 2)); // "ab"
    assertSpan(2, 4, a.toOriginalSpan(2, 3)); // the collapsed space covers both originals
    assertSpan(0, 4, a.toOriginalSpan(0, 3));
  }

  @Test
  void testInteriorDeletionDoesNotOverCover() {
    // "a b c" -> "abc" : the two spaces are deleted. A per-character offset map over-covers here.
    final Alignment a = new Alignment.Builder()
        .equal(1).replace(1, 0).equal(1).replace(1, 0).equal(1).build(5);
    assertEquals(3, a.normalizedLength());
    assertEquals(5, a.originalLength());
    assertSpan(0, 1, a.toOriginalSpan(0, 1)); // "a"
    assertSpan(2, 3, a.toOriginalSpan(1, 2)); // "b" -> [2,3), NOT [2,4)
    assertSpan(4, 5, a.toOriginalSpan(2, 3)); // "c"
    assertSpan(0, 5, a.toOriginalSpan(0, 3)); // whole text
  }

  @Test
  void testTrailingDeletionDoesNotOverCover() {
    // "ab  " -> "ab" : strip trailing spaces. A match at the end must not absorb them.
    final Alignment a = new Alignment.Builder().equal(2).replace(2, 0).build(4);
    assertSpan(0, 2, a.toOriginalSpan(0, 2)); // "ab" -> [0,2), NOT [0,4)
    assertSpan(1, 2, a.toOriginalSpan(1, 2)); // "b" -> [1,2)
  }

  @Test
  void testExpansionSharesTheSingleSource() {
    // "aßb" -> "assb" : the eszett expands to two characters that both come from it.
    final Alignment a = new Alignment.Builder().equal(1).replace(1, 2).equal(1).build(3);
    assertEquals(4, a.normalizedLength());
    assertSpan(1, 2, a.toOriginalSpan(1, 3)); // "ss" -> the single "ß"
    assertSpan(1, 2, a.toOriginalSpan(1, 2)); // first "s"
    assertSpan(1, 2, a.toOriginalSpan(2, 3)); // second "s"
    assertSpan(2, 3, a.toOriginalSpan(3, 4)); // "b"
  }

  @Test
  void testReverseMappingAndDeletionsMapToEmptySpans() {
    final Alignment a = new Alignment.Builder()
        .equal(1).replace(1, 0).equal(1).replace(1, 0).equal(1).build(5); // "a b c" -> "abc"
    assertSpan(1, 2, a.toNormalizedSpan(2, 3)); // original "b" -> normalized "b"
    assertSpan(1, 1, a.toNormalizedSpan(1, 2)); // deleted space -> empty normalized span
    assertSpan(0, 3, a.toNormalizedSpan(0, 5)); // whole original -> whole normalized
  }

  @Test
  void testAndThenComposesTwoStages() {
    // Stage 1: "a  b" -> "a b" (collapse two spaces). Stage 2: "a b" -> "a-b" (space to dash).
    final Alignment whitespace = new Alignment.Builder().equal(1).replace(2, 1).equal(1).build(4);
    final Alignment dash = new Alignment.Builder().equal(1).replace(1, 1).equal(1).build(3);
    final Alignment composed = whitespace.andThen(dash);

    assertEquals(4, composed.originalLength());
    assertEquals(3, composed.normalizedLength());
    assertSpan(0, 1, composed.toOriginalSpan(0, 1)); // "a"
    assertSpan(1, 3, composed.toOriginalSpan(1, 2)); // "-" maps back to the original "  "
    assertSpan(3, 4, composed.toOriginalSpan(2, 3)); // "b"
    assertSpan(0, 4, composed.toOriginalSpan(0, 3));
  }

  @Test
  void testAndThenRejectsMismatchedStages() {
    final Alignment first = new Alignment.Builder().equal(2).build(2);  // normalizedLength 2
    final Alignment second = new Alignment.Builder().equal(3).build(3); // originalLength 3
    assertThrows(IllegalArgumentException.class, () -> first.andThen(second));
  }

  @Test
  void testAllDeletedProducesEmptyNormalized() {
    final Alignment a = new Alignment.Builder().replace(2, 0).build(2); // "  " -> ""
    assertEquals(0, a.normalizedLength());
    assertEquals(2, a.originalLength());
    assertSpan(0, 0, a.toNormalizedSpan(0, 2)); // all original deleted -> empty normalized span
  }

  @Test
  void testBuilderRejectsWrongOriginalLength() {
    assertThrows(IllegalStateException.class, () -> new Alignment.Builder().equal(2).build(3));
  }

  @Test
  void testBuilderRejectsNegativeCounts() {
    assertThrows(IllegalArgumentException.class, () -> new Alignment.Builder().equal(-1));
    assertThrows(IllegalArgumentException.class, () -> new Alignment.Builder().replace(-1, 0));
  }

  @Test
  void testToOriginalSpanRejectsOutOfRange() {
    final Alignment a = new Alignment.Builder().equal(2).build(2);
    assertThrows(IndexOutOfBoundsException.class, () -> a.toOriginalSpan(-1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> a.toOriginalSpan(0, 3));
    assertThrows(IndexOutOfBoundsException.class, () -> a.toOriginalSpan(2, 1));
  }

  @Test
  void testToOriginalOffsetConvenience() {
    final Alignment a = new Alignment.Builder().equal(2).replace(2, 1).build(4); // "ab  "->"ab "
    assertEquals(0, a.toOriginalOffset(0));
    assertEquals(2, a.toOriginalOffset(2)); // start of the collapsed space
    assertEquals(4, a.toOriginalOffset(3)); // end sentinel -> original length
  }

  @Test
  void testBuilderGrowsBeyondInitialCapacity() {
    // 20 equal chars force the builder past its initial 16-entry buffers (exercises grow()).
    final Alignment a = new Alignment.Builder().equal(20).build(20);
    assertEquals(20, a.normalizedLength());
    assertEquals(20, a.originalLength());
    assertSpan(0, 20, a.toOriginalSpan(0, 20));
    assertSpan(17, 18, a.toOriginalSpan(17, 18));
  }

  @Test
  void testAndThenChainsThreeStages() {
    // "a  b" -> "a b" (collapse) -> "a-b" (space->dash) -> "a_b" (dash->underscore).
    final Alignment s1 = new Alignment.Builder().equal(1).replace(2, 1).equal(1).build(4);
    final Alignment s2 = new Alignment.Builder().equal(1).replace(1, 1).equal(1).build(3);
    final Alignment s3 = new Alignment.Builder().equal(1).replace(1, 1).equal(1).build(3);
    final Alignment composed = s1.andThen(s2).andThen(s3);

    assertEquals(4, composed.originalLength());
    assertEquals(3, composed.normalizedLength());
    assertSpan(0, 1, composed.toOriginalSpan(0, 1)); // a
    assertSpan(1, 3, composed.toOriginalSpan(1, 2)); // "_" maps all the way back to the "  "
    assertSpan(3, 4, composed.toOriginalSpan(2, 3)); // b
  }

  @Test
  void testToNormalizedSpanAcrossExpansion() {
    final Alignment a = new Alignment.Builder().equal(1).replace(1, 2).equal(1).build(3); // ß->ss
    assertSpan(1, 3, a.toNormalizedSpan(1, 2)); // original "ß" -> the two-char "ss"
    assertSpan(0, 1, a.toNormalizedSpan(0, 1)); // a
    assertSpan(3, 4, a.toNormalizedSpan(2, 3)); // b
  }
}
