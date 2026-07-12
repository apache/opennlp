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
package opennlp.subword.sentencepiece;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Viterbi segmentation under a unigram language model: of all ways to cover the normalized text
 * with vocabulary pieces, it finds the one with the highest total log-probability.
 *
 * <p>Characters no piece covers fall back to the unknown id with a fixed penalty below the lowest
 * piece score, and user-defined symbols receive a length-based bonus score so they always win.</p>
 */
final class UnigramEncoder {

  private static final float UNK_PENALTY = 10.0f;
  private static final float SCORE_RESET_THRESHOLD = 100000.0f;

  private final PieceTrie trie;
  private final float[] scores;
  private final boolean[] unused;
  private final boolean[] userDefined;
  private final float unkScore;
  private final int unkId;

  /**
   * Instantiates the encoder.
   *
   * @param trie        The vocabulary trie over all matchable pieces.
   * @param scores      The log-probability score of every piece, indexed by id.
   * @param unused      Whether each id has the unused piece type.
   * @param userDefined Whether each id is a user-defined symbol.
   * @param minScore    The lowest score among normal pieces.
   * @param unkId       The id of the unknown piece.
   */
  UnigramEncoder(PieceTrie trie, float[] scores, boolean[] unused, boolean[] userDefined,
                 float minScore, int unkId) {
    this.trie = trie;
    this.scores = scores;
    this.unused = unused;
    this.userDefined = userDefined;
    this.unkScore = minScore - UNK_PENALTY;
    this.unkId = unkId;
  }

  /**
   * Segments normalized text.
   *
   * @param normalized The buffer holding the normalized UTF-8 bytes; must not be null.
   * @param size       The number of valid bytes in {@code normalized}.
   * @return The best-path segments covering all bytes, in text order.
   */
  List<Segment> encode(byte[] normalized, int size) {
    if (size == 0) {
      return List.of();
    }

    // The best path ending at each byte position (exclusive end), interleaved as
    // [startsAt, scoreBits, id] triples so a frontier update touches one cache line. Scores
    // travel as raw float bits, a lossless round trip; all arithmetic happens on the floats.
    final int[] best = new int[3 * (size + 1)];
    for (int i = 0; i <= size; i++) {
      best[3 * i] = -1;
    }
    best[1] = Float.floatToRawIntBits(0.0f);

    int startsAt = 0;
    int maxFrontier = 0;
    while (startsAt < size) {
      float bestScoreTillHere = Float.intBitsToFloat(best[3 * startsAt + 1]);
      if (bestScoreTillHere < -SCORE_RESET_THRESHOLD
          || bestScoreTillHere > SCORE_RESET_THRESHOLD) {
        // Re-bases accumulated scores to keep float precision on very long inputs; every
        // reachable frontier position shifts by the same offset, so the argmax is unchanged.
        final float offset = bestScoreTillHere;
        for (int i = startsAt; i <= maxFrontier; i++) {
          if (i == startsAt || best[3 * i] != -1) {
            best[3 * i + 1] = Float.floatToRawIntBits(
                Float.intBitsToFloat(best[3 * i + 1]) - offset);
          }
        }
        bestScoreTillHere = 0.0f;
      }

      boolean hasSingleNode = false;
      final int mblen = Math.min(SentencePieceNormalizer.utf8Length(normalized[startsAt]),
          size - startsAt);

      int node = trie.root();
      for (int keyPos = startsAt; keyPos < size; ) {
        node = trie.step(node, normalized[keyPos]);
        if (node == PieceTrie.DEAD) {
          break;
        }
        keyPos++;
        final int id = trie.value(node);
        if (id < 0) {
          continue;
        }
        if (unused[id]) {
          continue;
        }
        maxFrontier = Math.max(maxFrontier, keyPos);
        final int length = keyPos - startsAt;
        // User-defined symbols receive a length bonus instead of a trained score.
        final float score = userDefined[id] ? 0.1f * (length - 1) : scores[id];
        final float candidate = score + bestScoreTillHere;
        final int slot = 3 * keyPos;
        if (best[slot] == -1 || candidate > Float.intBitsToFloat(best[slot + 1])) {
          best[slot + 1] = Float.floatToRawIntBits(candidate);
          best[slot] = startsAt;
          best[slot + 2] = id;
        }
        if (!hasSingleNode && length == mblen) {
          hasSingleNode = true;
        }
      }

      if (!hasSingleNode) {
        final int end = startsAt + mblen;
        maxFrontier = Math.max(maxFrontier, end);
        final float candidate = unkScore + bestScoreTillHere;
        final int slot = 3 * end;
        if (best[slot] == -1 || candidate > Float.intBitsToFloat(best[slot + 1])) {
          best[slot + 1] = Float.floatToRawIntBits(candidate);
          best[slot] = startsAt;
          best[slot + 2] = unkId;
        }
      }

      startsAt += mblen;
    }

    final List<Segment> results = new ArrayList<>(size / 4 + 1);
    int endsAt = size;
    while (endsAt > 0) {
      final int from = best[3 * endsAt];
      if (from < 0) {
        throw new IllegalStateException(
            "The Viterbi path is broken at normalized byte " + endsAt + ".");
      }
      results.add(new Segment(from, endsAt, best[3 * endsAt + 2]));
      endsAt = from;
    }
    Collections.reverse(results);
    return results;
  }
}
