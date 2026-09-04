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

import java.util.ArrayDeque;
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

  /**
   * Creates a lexicon from completed double-array tables and entry lists.
   *
   * @param base The transition offsets.
   * @param check The parent state for each occupied index.
   * @param codeOf The dense label for each UTF-16 character.
   * @param values The entries for each surface.
   */
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
   * Builds the trie from sorted surface ranges. Each node places children at a common
   * free base, and a moving watermark makes that search near-linear over real
   * lexicons. The traversal uses an explicit stack so a long surface cannot exhaust
   * the thread stack.
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

    /**
     * Initializes storage for the sorted surfaces and their dense character codes.
     *
     * @param surfaces The sorted surface forms.
     * @param codeOf The dense label for each UTF-16 character.
     */
    private Builder(String[] surfaces, int[] codeOf) {
      this.surfaces = surfaces;
      this.codeOf = codeOf;
      base = new int[1 << 16];
      check = new int[1 << 16];
      Arrays.fill(check, EMPTY);
    }

    /**
     * A surface range with children waiting to be placed.
     *
     * @param left The first surface in the range.
     * @param right The exclusive end of the range.
     * @param depth The character depth of the node.
     * @param state The node's array index.
     */
    private record PendingNode(int left, int right, int depth, int state) {
    }

    /**
     * Places one trie and the descendants without consuming the thread stack.
     *
     * @param left The first surface of the node's range.
     * @param right The exclusive last surface of the node's range.
     * @param depth The character depth of the node.
     * @param state The node's own slot.
     */
    private void insert(int left, int right, int depth, int state) {
      final ArrayDeque<PendingNode> pending = new ArrayDeque<>();
      pending.push(new PendingNode(left, right, depth, state));
      while (!pending.isEmpty()) {
        final PendingNode node = pending.pop();
        final int[] labels = new int[node.right() - node.left()];
        int labelCount = 0;
        int previous = -2;
        for (int k = node.left(); k < node.right(); k++) {
          final int label = surfaces[k].length() == node.depth()
              ? 0 : codeOf[surfaces[k].charAt(node.depth())];
          if (label != previous) {
            labels[labelCount++] = label;
            previous = label;
          }
        }
        final int found = findBase(labels, labelCount);
        base[node.state()] = found;
        for (int k = 0; k < labelCount; k++) {
          final int child = found + labels[k];
          check[child] = node.state();
          if (child > high) {
            high = child;
          }
        }

        int childEnd = node.right();
        for (int k = labelCount - 1; k >= 0; k--) {
          final int label = labels[k];
          int childStart = childEnd - 1;
          while (childStart > node.left()
              && (surfaces[childStart - 1].length() == node.depth()
                  ? 0 : codeOf[surfaces[childStart - 1].charAt(node.depth())]) == label) {
            childStart--;
          }
          final int child = found + label;
          if (label == 0) {
            base[child] = -(++valueIndex);
          } else {
            pending.push(new PendingNode(
                childStart, childEnd, node.depth() + 1, child));
          }
          childEnd = childStart;
        }
      }
    }

    /**
     * Finds the lowest base at which all labels use free indices. Labels are in
     * surface-character order, so this method computes the numeric bounds.
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
