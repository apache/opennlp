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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OffsetMapTest {

  // Builds the map for a collapsing pass that turns "a   b" (three spaces) into "a b": the single
  // normalized space is attributed to the original offset where the run started.
  private static OffsetMap collapsedRun() {
    final OffsetMap.Builder b = new OffsetMap.Builder();
    b.map(0); // normalized 'a'   <- original 0
    b.map(1); // normalized ' '   <- original 1 (start of the three-space run)
    b.map(4); // normalized 'b'   <- original 4
    return b.build(5);
  }

  @Test
  void testOffsetInsideCollapsedRunMapsToTheRunsSingleChar() {
    final OffsetMap map = collapsedRun();
    assertEquals(0, map.toNormalizedOffset(0)); // 'a'
    assertEquals(1, map.toNormalizedOffset(1)); // run start
    assertEquals(1, map.toNormalizedOffset(2)); // inside the run, must not jump to 'b'
    assertEquals(1, map.toNormalizedOffset(3)); // inside the run
    assertEquals(2, map.toNormalizedOffset(4)); // 'b'
    assertEquals(3, map.toNormalizedOffset(5)); // end sentinel
  }

  @Test
  void testReverseDirectionReadsTheRunStart() {
    final OffsetMap map = collapsedRun();
    assertEquals(0, map.toOriginalOffset(0));
    assertEquals(1, map.toOriginalOffset(1)); // the collapsed space reports the run start
    assertEquals(4, map.toOriginalOffset(2));
    assertEquals(5, map.toOriginalOffset(3));
  }

  @Test
  void testOffsetBeforeFirstRetainedCharClampsToZero() {
    // "  a" with leading whitespace trimmed -> "a"; the only normalized char came from original 2.
    final OffsetMap.Builder b = new OffsetMap.Builder();
    b.map(2);
    final OffsetMap map = b.build(3);
    assertEquals(0, map.toNormalizedOffset(0)); // before the first retained char -> clamp to 0
    assertEquals(0, map.toNormalizedOffset(1));
    assertEquals(0, map.toNormalizedOffset(2)); // the 'a'
    assertEquals(1, map.toNormalizedOffset(3)); // end
  }

  @Test
  void testIdentityMappingRoundTrips() {
    final OffsetMap.Builder b = new OffsetMap.Builder();
    b.map(0);
    b.map(1);
    b.map(2);
    final OffsetMap map = b.build(3);
    for (int i = 0; i <= 3; i++) {
      assertEquals(i, map.toOriginalOffset(i));
      assertEquals(i, map.toNormalizedOffset(i));
    }
  }

  @Test
  void testOutOfRangeOffsetsThrow() {
    final OffsetMap map = collapsedRun();
    assertThrows(IndexOutOfBoundsException.class, () -> map.toNormalizedOffset(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> map.toNormalizedOffset(6));
    assertThrows(IndexOutOfBoundsException.class, () -> map.toOriginalOffset(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> map.toOriginalOffset(4));
  }
}
