/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChunkRangesTest {

  @Test
  void testCreatesOverlappingChunkRanges() {
    final List<AbstractDL.ChunkRange> ranges = AbstractDL.chunkRanges(10, 4, 1);

    assertEquals(List.of(
        new AbstractDL.ChunkRange(0, 4),
        new AbstractDL.ChunkRange(3, 7),
        new AbstractDL.ChunkRange(6, 10)), ranges);
  }

  @Test
  void testCreatesTrailingPartialChunkRange() {
    final List<AbstractDL.ChunkRange> ranges = AbstractDL.chunkRanges(9, 4, 1);

    assertEquals(List.of(
        new AbstractDL.ChunkRange(0, 4),
        new AbstractDL.ChunkRange(3, 7),
        new AbstractDL.ChunkRange(6, 9)), ranges);
  }

  @Test
  void testDoesNotCreateRedundantTrailingChunkRange() {
    final List<AbstractDL.ChunkRange> ranges = AbstractDL.chunkRanges(10, 8, 4);

    assertEquals(List.of(
        new AbstractDL.ChunkRange(0, 8),
        new AbstractDL.ChunkRange(4, 10)), ranges);
  }

  @Test
  void testCreatesAdjacentChunkRangesWithoutOverlap() {
    // splitOverlapSize == 0: chunks partition the tokens end-to-end with no shared tokens.
    final List<AbstractDL.ChunkRange> ranges = AbstractDL.chunkRanges(10, 5, 0);

    assertEquals(List.of(
        new AbstractDL.ChunkRange(0, 5),
        new AbstractDL.ChunkRange(5, 10)), ranges);
  }

  @Test
  void testCreatesSingleChunkWhenInputFitsInSplit() {
    // Fewer tokens than the split size, and exactly the split size, both yield one chunk.
    assertEquals(List.of(new AbstractDL.ChunkRange(0, 4)), AbstractDL.chunkRanges(4, 8, 2));
    assertEquals(List.of(new AbstractDL.ChunkRange(0, 8)), AbstractDL.chunkRanges(8, 8, 2));
  }

  @Test
  void testCreatesNoRangesForEmptyInput() {
    assertTrue(AbstractDL.chunkRanges(0, 4, 1).isEmpty());
  }

  @Test
  void testRejectsNegativeTokenCount() {
    assertThrows(IllegalArgumentException.class, () -> AbstractDL.chunkRanges(-1, 4, 1));
  }
}
