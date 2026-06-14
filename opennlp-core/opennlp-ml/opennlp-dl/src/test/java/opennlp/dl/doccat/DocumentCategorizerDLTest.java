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

package opennlp.dl.doccat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.WordpieceTokenizer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DocumentCategorizerDLTest {

  private static Map<String, Integer> vocab() {
    final Map<String, Integer> vocab = new HashMap<>();
    vocab.put(WordpieceTokenizer.BERT_CLS_TOKEN, 0);
    vocab.put(WordpieceTokenizer.BERT_SEP_TOKEN, 1);
    vocab.put(WordpieceTokenizer.BERT_UNK_TOKEN, 2);
    vocab.put("hello", 3);
    vocab.put("world", 4);
    return vocab;
  }

  @Test
  void testTokenIdsMapsTokensToVocabularyIds() {
    final long[] ids = DocumentCategorizerDL.tokenIds(
        new String[] {WordpieceTokenizer.BERT_CLS_TOKEN, "hello", "world",
            WordpieceTokenizer.BERT_SEP_TOKEN}, vocab());

    assertArrayEquals(new long[] {0, 3, 4, 1}, ids);
  }

  @Test
  void testTokenIdsRejectsTokensMissingFromVocabulary() {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
        DocumentCategorizerDL.tokenIds(new String[] {"hello", "missing"}, vocab()));

    assertTrue(e.getMessage().contains("missing"),
        "the error message should name the missing token: " + e.getMessage());
  }

  @Test
  void testSoftmaxIsUniformForEqualLogitsAndSumsToOne() {
    final double[] out = DocumentCategorizerDL.softmax(new float[] {0f, 0f, 0f});

    assertEquals(3, out.length);
    for (final double p : out) {
      assertEquals(1.0 / 3.0, p, 1e-12);
    }
    assertEquals(1.0, out[0] + out[1] + out[2], 1e-12);
  }

  @Test
  void testSoftmaxIsNumericallyStableForLargeLogits() {
    // The naive exp(logit) form overflows to +Infinity here and yields NaN; subtracting
    // the maximum keeps every value finite and the distribution uniform.
    final double[] out = DocumentCategorizerDL.softmax(new float[] {1000f, 1000f, 1000f});

    double sum = 0.0;
    for (final double p : out) {
      assertFalse(Double.isNaN(p) || Double.isInfinite(p),
          "softmax must stay finite for large logits");
      assertEquals(1.0 / 3.0, p, 1e-9);
      sum += p;
    }
    assertEquals(1.0, sum, 1e-12);
  }

  @Test
  void testSoftmaxMatchesReferenceDistribution() {
    // Reference (numpy): softmax([1,2,3]) = [0.09003057, 0.24472847, 0.66524096].
    final double[] out = DocumentCategorizerDL.softmax(new float[] {1f, 2f, 3f});

    assertEquals(0.09003057, out[0], 1e-6);
    assertEquals(0.24472847, out[1], 1e-6);
    assertEquals(0.66524096, out[2], 1e-6);
  }

  @Test
  void testChunkRangesSplitsWithOverlap() {
    // 210 words, 200-word chunks overlapping by 50 -> [0,200), [150,210).
    final List<int[]> ranges = DocumentCategorizerDL.chunkRanges(210, 200, 50);

    assertEquals(2, ranges.size());
    assertArrayEquals(new int[] {0, 200}, ranges.get(0));
    assertArrayEquals(new int[] {150, 210}, ranges.get(1));
  }

  @Test
  void testChunkRangesSingleChunkWhenShorterThanSplit() {
    final List<int[]> ranges = DocumentCategorizerDL.chunkRanges(30, 200, 50);

    assertEquals(1, ranges.size());
    assertArrayEquals(new int[] {0, 30}, ranges.get(0));
  }

  @Test
  void testChunkRangesEmptyForZeroLength() {
    assertTrue(DocumentCategorizerDL.chunkRanges(0, 200, 50).isEmpty());
  }

  @Test
  void testChunkRangesAlwaysProgressesForInvalidOverlap() {
    // overlap == split would stall forever, and overlap > split would make the start index
    // negative, without the forward-progress guard.
    for (final int[] cfg : new int[][] {{10, 5, 5}, {8, 3, 10}, {7, 4, 100}}) {
      final int length = cfg[0];
      final List<int[]> ranges = DocumentCategorizerDL.chunkRanges(length, cfg[1], cfg[2]);

      int previousStart = -1;
      for (final int[] range : ranges) {
        assertTrue(range[0] >= 0, "start must never be negative: " + range[0]);
        assertTrue(range[1] >= range[0], "end must be >= start");
        assertTrue(range[0] > previousStart, "each chunk must advance the start index");
        previousStart = range[0];
      }
      assertEquals(length, ranges.get(ranges.size() - 1)[1], "last chunk must reach the end");
    }
  }
}
