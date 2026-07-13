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

import java.util.Arrays;
import java.util.Comparator;

/**
 * An immutable byte-level trie over vocabulary pieces, packed into flat arrays.
 *
 * <p>Encoding walks it one byte at a time ({@link #step(int, byte)}), so every piece that starts
 * at a given input position is enumerated in one forward pass. Wide nodes dispatch through a
 * 256-entry direct table and narrow nodes scan a short sorted label slice; both layouts enumerate
 * identical transitions.</p>
 */
final class PieceTrie {

  /** The node id returned when no transition exists. */
  static final int DEAD = -1;

  // A node dispatches through a 256-entry slice of directPool when it has more children than
  // this; otherwise a linear scan of the sorted label slice is used.
  private static final int DIRECT_THRESHOLD = 8;

  // Per node: the slice [childStart[n], childStart[n + 1]) of labels/childNodes, and the piece id
  // accepted at the node, or -1. Wide nodes additionally index directPool at directStart[n].
  private final int[] childStart;
  private final byte[] labels;
  private final int[] childNodes;
  private final int[] values;
  private final int[] directStart;
  private final int[] directPool;

  private PieceTrie(int[] childStart, byte[] labels, int[] childNodes, int[] values) {
    this.childStart = childStart;
    this.labels = labels;
    this.childNodes = childNodes;
    this.values = values;
    this.directStart = new int[values.length];
    int wide = 0;
    for (int node = 0; node < values.length; node++) {
      if (childStart[node + 1] - childStart[node] > DIRECT_THRESHOLD) {
        directStart[node] = wide * 256;
        wide++;
      } else {
        directStart[node] = -1;
      }
    }
    this.directPool = new int[wide * 256];
    java.util.Arrays.fill(directPool, DEAD);
    for (int node = 0; node < values.length; node++) {
      final int direct = directStart[node];
      if (direct >= 0) {
        for (int edge = childStart[node]; edge < childStart[node + 1]; edge++) {
          directPool[direct + (labels[edge] & 0xFF)] = childNodes[edge];
        }
      }
    }
  }

  /**
   * Builds a trie from pieces and their ids.
   *
   * @param pieces The UTF-8 bytes of each piece; must not be null or contain empty keys.
   * @param ids    The id stored for each piece, parallel to {@code pieces}.
   * @return The packed trie.
   */
  static PieceTrie build(byte[][] pieces, int[] ids) {
    final Integer[] order = new Integer[pieces.length];
    for (int i = 0; i < order.length; i++) {
      order[i] = i;
    }
    Arrays.sort(order, Comparator.comparing(i -> pieces[i], Arrays::compareUnsigned));

    // First pass counts nodes and edges, second pass fills the packed arrays; both walk the
    // sorted keys with the same recursion, so the shapes agree by construction.
    final Builder builder = new Builder(pieces, ids, order);
    builder.count(0, pieces.length, 0);
    builder.allocate();
    builder.fill(0, pieces.length, 0);
    return new PieceTrie(builder.childStart, builder.labels, builder.childNodes, builder.values);
  }

  /** {@return the root node id} */
  int root() {
    return 0;
  }

  /**
   * Follows the transition labeled {@code b}.
   *
   * @param node The current node id.
   * @param b    The next key byte.
   * @return The child node id, or {@link #DEAD} when no such transition exists.
   */
  int step(int node, byte b) {
    final int direct = directStart[node];
    if (direct >= 0) {
      return directPool[direct + (b & 0xFF)];
    }
    final int to = childStart[node + 1];
    for (int edge = childStart[node]; edge < to; edge++) {
      if (labels[edge] == b) {
        return childNodes[edge];
      }
    }
    return DEAD;
  }

  /**
   * Returns the piece id accepted at a node.
   *
   * @param node The node id.
   * @return The id, or {@code -1} when the node accepts no piece.
   */
  int value(int node) {
    return values[node];
  }

  // Builds the packed form from keys sorted by unsigned byte order. Key ranges sharing a prefix
  // are contiguous after the sort, so each recursion partitions its range by the byte at the
  // current depth.
  private static final class Builder {

    private final byte[][] pieces;
    private final int[] ids;
    private final Integer[] order;

    private int nodeCount;
    private int edgeCount;

    private int[] childStart;
    private byte[] labels;
    private int[] childNodes;
    private int[] values;
    private int nextNode;
    private int nextEdge;

    Builder(byte[][] pieces, int[] ids, Integer[] order) {
      this.pieces = pieces;
      this.ids = ids;
      this.order = order;
    }

    /**
     * Counts the nodes and edges of the subtrie for the sorted key range {@code [from, to)} at the
     * given depth.
     *
     * @param from  The inclusive start index into {@code order}.
     * @param to    The exclusive end index into {@code order}.
     * @param depth The byte depth this node partitions on.
     * @throws IllegalArgumentException Thrown if a key is defined more than once.
     */
    void count(int from, int to, int depth) {
      nodeCount++;
      int i = from;
      if (i < to && pieces[order[i]].length == depth) {
        i++;
        // A second key ending at the same depth is a duplicate; the sort made them adjacent.
        if (i < to && pieces[order[i]].length == depth) {
          throw new IllegalArgumentException("The piece '"
              + new String(pieces[order[i]], java.nio.charset.StandardCharsets.UTF_8)
              + "' is defined more than once.");
        }
      }
      while (i < to) {
        final byte label = pieces[order[i]][depth];
        int j = i;
        while (j < to && pieces[order[j]][depth] == label) {
          j++;
        }
        edgeCount++;
        count(i, j, depth + 1);
        i = j;
      }
    }

    /** Allocates the packed arrays to the node and edge counts gathered by {@link #count}. */
    void allocate() {
      childStart = new int[nodeCount + 1];
      labels = new byte[edgeCount];
      childNodes = new int[edgeCount];
      values = new int[nodeCount];
    }

    /**
     * Fills the packed arrays for the sorted key range {@code [from, to)} at the given depth and
     * returns the node id assigned to it.
     *
     * @param from  The inclusive start index into {@code order}.
     * @param to    The exclusive end index into {@code order}.
     * @param depth The byte depth this node partitions on.
     * @return The id of the node created for this range.
     * @throws IllegalArgumentException Thrown if a key is defined more than once.
     */
    int fill(int from, int to, int depth) {
      final int node = nextNode++;
      values[node] = -1;
      int i = from;
      if (i < to && pieces[order[i]].length == depth) {
        if (values[node] != -1 || (i + 1 < to && pieces[order[i + 1]].length == depth)) {
          throw new IllegalArgumentException(
              "The piece '" + new String(pieces[order[i]], java.nio.charset.StandardCharsets.UTF_8)
                  + "' is defined more than once.");
        }
        values[node] = ids[order[i]];
        i++;
      }
      // Reserve this node's edge slice before recursing so siblings stay contiguous.
      final int sliceStart = nextEdge;
      int sliceCount = 0;
      int scan = i;
      while (scan < to) {
        final byte label = pieces[order[scan]][depth];
        int j = scan;
        while (j < to && pieces[order[j]][depth] == label) {
          j++;
        }
        sliceCount++;
        scan = j;
      }
      nextEdge += sliceCount;
      childStart[node] = sliceStart;
      childStart[node + 1] = nextEdge;

      int edge = sliceStart;
      while (i < to) {
        final byte label = pieces[order[i]][depth];
        int j = i;
        while (j < to && pieces[order[j]][depth] == label) {
          j++;
        }
        labels[edge] = label;
        childNodes[edge] = fill(i, j, depth + 1);
        edge++;
        i = j;
      }
      return node;
    }
  }
}
