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

package opennlp.tools.tokenize.lattice;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import opennlp.tools.tokenize.lattice.MecabDictionary.PrefixMatchConsumer;
import opennlp.tools.tokenize.lattice.MecabDictionary.WordEntry;

/**
 * The lexicon as a double-array trie: one transition is one array read and one
 * comparison. Characters are recoded into dense labels ordered by descending
 * frequency before the array is built, which keeps the array compact; a character
 * absent from the lexicon misses in the recode table before the array is consulted.
 *
 * <p>The layout is the classic base/check pair: from state {@code s}, label
 * {@code c} leads to {@code t = base[s] + c} exactly when {@code check[t] == s}.
 * Label {@code 0} terminates a surface and leads to a state. Its negative base
 * encodes the index of the surface's entry list.</p>
 */
final class DoubleArrayLexicon {

  private final int[] base;
  private final int[] check;
  private final int[] codeOf;
  private final List<WordEntry>[] values;

  private DoubleArrayLexicon(int[] base, int[] check, int[] codeOf,
      List<WordEntry>[] values) {
    this.base = base;
    this.check = check;
    this.codeOf = codeOf;
    this.values = values;
  }

  /**
   * Builds the trie from the surface-keyed lexicon.
   *
   * @param lexicon The entries keyed by surface form.
   * @return The built trie. Not {@code null}.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  static DoubleArrayLexicon build(Map<String, List<WordEntry>> lexicon) {
    final String[] surfaces = lexicon.keySet().toArray(new String[0]);
    Arrays.sort(surfaces);
    final List<WordEntry>[] values = new List[surfaces.length];
    for (int i = 0; i < surfaces.length; i++) {
      values[i] = List.copyOf(lexicon.get(surfaces[i]));
    }

    // Dense recode: labels ordered by descending frequency get the small codes, so
    // busy transitions cluster at the low end of the array.
    final int[] frequency = new int[Character.MAX_VALUE + 1];
    for (final String surface : surfaces) {
      for (int i = 0; i < surface.length(); i++) {
        frequency[surface.charAt(i)]++;
      }
    }
    final long[] rankedCharacters = new long[Character.MAX_VALUE + 1];
    int distinct = 0;
    for (int c = 0; c <= Character.MAX_VALUE; c++) {
      if (frequency[c] > 0) {
        rankedCharacters[distinct++] =
            ((long) (Integer.MAX_VALUE - frequency[c]) << 16) | c;
      }
    }
    Arrays.sort(rankedCharacters, 0, distinct);
    final int[] codeOf = new int[Character.MAX_VALUE + 1];
    Arrays.fill(codeOf, -1);
    for (int rank = 0; rank < distinct; rank++) {
      codeOf[(int) (rankedCharacters[rank] & Character.MAX_VALUE)] = rank + 1;
    }

    final Builder builder = new Builder(surfaces, codeOf);
    builder.insert(0, surfaces.length, 0, Builder.ROOT);
    return new DoubleArrayLexicon(Arrays.copyOf(builder.base, builder.high + 1),
        Arrays.copyOf(builder.check, builder.high + 1), codeOf, values);
  }

  /**
   * Reports every surface starting at a text position, walking the array once.
   *
   * @param text The text being segmented.
   * @param from The position surfaces must start at.
   * @param to The exclusive end of the searchable stretch.
   * @param consumer Receives each match length with its entries.
   */
  void prefixMatches(String text, int from, int to,
      PrefixMatchConsumer consumer) {
    int state = Builder.ROOT;
    for (int i = from; i < to; i++) {
      final char c = text.charAt(i);
      final int code = codeOf[c];
      if (code < 0) {
        return;
      }
      final int next = base[state] + code;
      if (next >= check.length || check[next] != state) {
        return;
      }
      state = next;
      final int terminal = base[state];
      if (terminal < check.length && check[terminal] == state && base[terminal] < 0) {
        consumer.accept(i - from + 1, values[-base[terminal] - 1]);
      }
    }
  }

  /**
   * The recursive sorted-range builder: each call places one node's children by
   * finding a base at which every child label uses a free slot, then recurses
   * per child range. A moving watermark keeps the free-slot search near-linear over
   * real lexicons.
   */
  private static final class Builder {

    private static final int ROOT = 1;
    private static final int EMPTY = -1;

    private final String[] surfaces;
    private final int[] codeOf;
    private int[] base;
    private int[] check;
    private int high = ROOT;
    private int watermark = ROOT + 1;
    private int valueIndex;

    private Builder(String[] surfaces, int[] codeOf) {
      this.surfaces = surfaces;
      this.codeOf = codeOf;
      base = new int[1 << 16];
      check = new int[1 << 16];
      Arrays.fill(check, EMPTY);
    }

    /**
     * Places the children of one trie node.
     *
     * @param left The first surface of the node's range.
     * @param right The exclusive last surface of the node's range.
     * @param depth The character depth of the node.
     * @param state The node's own slot.
     */
    private void insert(int left, int right, int depth, int state) {
      // gather the distinct child labels of this range, terminator first
      final int[] labels = new int[right - left];
      int labelCount = 0;
      int previous = -2;
      for (int k = left; k < right; k++) {
        final int label = surfaces[k].length() == depth
            ? 0 : codeOf[surfaces[k].charAt(depth)];
        if (label != previous) {
          labels[labelCount++] = label;
          previous = label;
        }
      }
      final int found = findBase(labels, labelCount);
      base[state] = found;
      for (int k = 0; k < labelCount; k++) {
        final int child = found + labels[k];
        check[child] = state;
        if (child > high) {
          high = child;
        }
      }
      // recurse over each child's sub-range
      int start = left;
      for (int k = 0; k < labelCount; k++) {
        final int label = labels[k];
        int end = start;
        while (end < right && (surfaces[end].length() == depth
            ? 0 : codeOf[surfaces[end].charAt(depth)]) == label) {
          end++;
        }
        final int child = found + label;
        if (label == 0) {
          base[child] = -(++valueIndex);
        } else {
          insert(start, end, depth + 1, child);
        }
        start = end;
      }
    }

    /**
     * Finds the lowest base at which every label uses a free slot. Labels
     * arrive in surface-character order, not numeric order, so the smallest and
     * largest label are computed rather than assumed positional.
     *
     * @param labels The child labels to place.
     * @param labelCount How many leading elements of {@code labels} are in use.
     * @return The base offset every label fits at.
     */
    private int findBase(int[] labels, int labelCount) {
      int smallest = labels[0];
      int largest = labels[0];
      for (int k = 1; k < labelCount; k++) {
        smallest = Math.min(smallest, labels[k]);
        largest = Math.max(largest, labels[k]);
      }
      int candidate = Math.max(1, watermark - smallest);
      while (true) {
        ensureCapacity(candidate + largest);
        boolean fits = true;
        for (int k = 0; fits && k < labelCount; k++) {
          fits = check[candidate + labels[k]] == EMPTY;
        }
        if (fits) {
          while (watermark < check.length && check[watermark] != EMPTY) {
            watermark++;
          }
          return candidate;
        }
        candidate++;
      }
    }

    /**
     * Grows the base and check arrays until a slot is addressable.
     *
     * @param slot The highest slot index that has to be writable.
     */
    private void ensureCapacity(int slot) {
      if (slot >= check.length) {
        int capacity = check.length;
        while (capacity <= slot) {
          capacity += capacity >> 1;
        }
        base = Arrays.copyOf(base, capacity);
        final int old = check.length;
        check = Arrays.copyOf(check, capacity);
        Arrays.fill(check, old, capacity, EMPTY);
      }
    }
  }
}
