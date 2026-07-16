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

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Byte-pair-encoding segmentation: the normalized text starts as single characters (or
 * user-defined symbols, which are frozen whole) and adjacent pairs merge greedily, highest piece
 * score first, until no adjacent pair forms a vocabulary piece.
 *
 * <p>Only pieces of the normal, user-defined, and unused types participate in merges; a merge that
 * lands on an unused piece is re-segmented back into its constituents.</p>
 */
final class BpeEncoder implements Serializable {

  private static final long serialVersionUID = -57799941356582785L;

  private static final int MAX_RESEGMENT_DEPTH = 100;

  private final Map<String, Integer> pieces;
  private final float[] scores;
  private final boolean[] unused;
  private final boolean[] reserved;
  private final int unkId;
  private final PieceTrie userDefinedMatcher;

  /**
   * Instantiates the encoder.
   *
   * @param pieces             All pieces by content, mapping to their ids.
   * @param scores             The score of every piece, indexed by id.
   * @param unused             Whether each id has the unused piece type.
   * @param reserved           Whether each id is excluded from merging (any type other than
   *                           normal, user-defined, or unused).
   * @param unkId              The id of the unknown piece.
   * @param userDefinedMatcher Longest-match trie over user-defined symbols, or null when the
   *                           model defines none.
   */
  BpeEncoder(Map<String, Integer> pieces, float[] scores, boolean[] unused, boolean[] reserved,
             int unkId, PieceTrie userDefinedMatcher) {
    this.pieces = pieces;
    this.scores = scores;
    this.unused = unused;
    this.reserved = reserved;
    this.unkId = unkId;
    this.userDefinedMatcher = userDefinedMatcher;
  }

  /**
   * A candidate merge of the symbols at indices {@code left} and {@code right}; {@code size} is the
   * merged byte length, used to detect staleness after either side has changed.
   */
  private record Pair(int left, int right, float score, int size) {
  }

  /**
   * Segments normalized text.
   *
   * @param normalized The buffer holding the normalized UTF-8 bytes; must not be null.
   * @param size       The number of valid bytes in {@code normalized}.
   * @return The segments covering all bytes, in text order.
   * @throws IllegalArgumentException Thrown if {@code normalized} is null.
   */
  List<Segment> encode(byte[] normalized, int size) {
    if (normalized == null) {
      throw new IllegalArgumentException("The normalized buffer must not be null.");
    }
    if (size == 0) {
      return List.of();
    }

    // The symbol list as index-linked ranges of the normalized bytes; merged-away symbols
    // become empty ranges.
    final IntBuilder fromB = new IntBuilder(size);
    final IntBuilder toB = new IntBuilder(size);
    final List<Boolean> freezeList = new ArrayList<>();
    int position = 0;
    while (position < size) {
      int matched = 0;
      if (userDefinedMatcher != null) {
        matched = userDefinedMatcher.longestMatch(normalized, size, position);
      }
      final boolean frozen = matched > 0;
      final int length = frozen ? matched
          : Math.min(SentencePieceNormalizer.utf8Length(normalized[position]),
              size - position);
      fromB.append(position);
      toB.append(position + length);
      freezeList.add(frozen);
      position += length;
    }
    final int symbolCount = freezeList.size();
    final int[] from = fromB.toArray();
    final int[] to = toB.toArray();
    final int[] prev = new int[symbolCount];
    final int[] next = new int[symbolCount];
    final boolean[] freeze = new boolean[symbolCount];
    for (int i = 0; i < symbolCount; i++) {
      prev[i] = i - 1;
      next[i] = i + 1 < symbolCount ? i + 1 : -1;
      freeze[i] = freezeList.get(i);
    }

    // Higher score first; equal scores break towards the leftmost pair.
    final PriorityQueue<Pair> agenda = new PriorityQueue<>((a, b) -> {
      final int byScore = Float.compare(b.score(), a.score());
      return byScore != 0 ? byScore : Integer.compare(a.left(), b.left());
    });
    // Merged piece content mapped back to its two constituents, for re-segmenting unused pieces.
    final Map<String, String[]> revMerge = new HashMap<>();

    for (int left = 0; left + 1 < symbolCount; left++) {
      maybeAddPair(normalized, from, to, freeze, left, left + 1, agenda, revMerge);
    }

    while (!agenda.isEmpty()) {
      final Pair top = agenda.poll();
      // Skips entries made stale by an earlier merge of either side.
      if (from[top.left()] == to[top.left()] || from[top.right()] == to[top.right()]
          || to[top.left()] - from[top.left()] + to[top.right()] - from[top.right()]
             != top.size()) {
        continue;
      }

      // Replaces the pair with the merged symbol.
      to[top.left()] = to[top.right()];
      next[top.left()] = next[top.right()];
      if (next[top.right()] >= 0) {
        prev[next[top.right()]] = top.left();
      }
      from[top.right()] = to[top.right()];

      maybeAddPair(normalized, from, to, freeze, prev[top.left()], top.left(), agenda, revMerge);
      maybeAddPair(normalized, from, to, freeze, top.left(), next[top.left()], agenda, revMerge);
    }

    final List<Segment> output = new ArrayList<>(symbolCount);
    int consumed = 0;
    for (int index = 0; index != -1; index = next[index]) {
      final String piece =
          new String(normalized, from[index], to[index] - from[index], StandardCharsets.UTF_8);
      consumed = resegment(piece, consumed, 0, revMerge, output);
    }
    return output;
  }

  /**
   * Offers the adjacent symbol pair {@code (left, right)} as a merge candidate: the pair joins
   * the agenda only when the concatenation is a mergeable vocabulary piece, and a merge landing
   * on an unused piece is remembered in {@code revMerge} for later re-segmentation.
   *
   * @param normalized The buffer holding the normalized UTF-8 bytes.
   * @param from       Per symbol, the inclusive start offset in {@code normalized}.
   * @param to         Per symbol, the exclusive end offset in {@code normalized}.
   * @param freeze     Per symbol, whether it is a user-defined symbol excluded from merging.
   * @param left       The index of the left symbol, or {@code -1} for none.
   * @param right      The index of the right symbol, or {@code -1} for none.
   * @param agenda     The merge agenda to add to.
   * @param revMerge   The map from a merged piece to its two constituents.
   */
  private void maybeAddPair(byte[] normalized, int[] from, int[] to, boolean[] freeze,
                            int left, int right, PriorityQueue<Pair> agenda,
                            Map<String, String[]> revMerge) {
    if (left == -1 || right == -1 || freeze[left] || freeze[right]) {
      return;
    }
    final String piece =
        new String(normalized, from[left], to[right] - from[left], StandardCharsets.UTF_8);
    final Integer id = pieces.get(piece);
    if (id == null || id == unkId || reserved[id]) {
      return;
    }
    agenda.add(new Pair(left, right, scores[id], to[right] - from[left]));
    if (unused[id]) {
      revMerge.put(piece, new String[] {
          new String(normalized, from[left], to[left] - from[left], StandardCharsets.UTF_8),
          new String(normalized, from[right], to[right] - from[right], StandardCharsets.UTF_8)});
    }
  }

  /**
   * Emits a symbol, splitting a piece of the unused type back into the pieces it was merged from.
   * Positions are assigned by a running cursor; constituent byte lengths always sum to the merged
   * length, so the cursor stays aligned with the normalized bytes.
   *
   * @param piece    The piece content to emit.
   * @param consumed The running byte cursor into the normalized text.
   * @param depth    The current recursion depth.
   * @param revMerge The map from a merged piece to its two constituents.
   * @param output   The segment list to append to.
   * @return The updated byte cursor.
   */
  private int resegment(String piece, int consumed, int depth, Map<String, String[]> revMerge,
                        List<Segment> output) {
    final Integer mapped = pieces.get(piece);
    final int id = mapped == null ? unkId : mapped;
    final int byteLength = piece.getBytes(StandardCharsets.UTF_8).length;
    if (depth > MAX_RESEGMENT_DEPTH || !unused[id]) {
      output.add(new Segment(consumed, consumed + byteLength, id));
      return consumed + byteLength;
    }
    final String[] parts = revMerge.get(piece);
    if (parts == null) {
      output.add(new Segment(consumed, consumed + byteLength, id));
      return consumed + byteLength;
    }
    consumed = resegment(parts[0], consumed, depth + 1, revMerge, output);
    return resegment(parts[1], consumed, depth + 1, revMerge, output);
  }
}
