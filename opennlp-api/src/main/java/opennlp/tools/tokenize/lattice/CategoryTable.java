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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import opennlp.tools.tokenize.lattice.MecabDictionary.Category;

/**
 * The {@code char.def} code point to category name mapping over the Unicode
 * code point range.
 *
 * <p>The Basic Multilingual Plane is stored in a directly indexed array. The
 * supplementary planes are stored as a sorted, non-overlapping range table searched by
 * binary search, because dictionaries map them in a handful of large blocks.</p>
 */
final class CategoryTable {

  private final Category[] bmp;
  private final int[] rangeStart;
  private final int[] rangeEnd;
  private final Category[] rangeCategory;

  private CategoryTable(Category[] bmp, int[] rangeStart, int[] rangeEnd,
      Category[] rangeCategory) {
    this.bmp = bmp;
    this.rangeStart = rangeStart;
    this.rangeEnd = rangeEnd;
    this.rangeCategory = rangeCategory;
  }

  /**
   * Looks up the category a {@code char.def} mapping gives a code point. The table
   * contains the {@link Category} instances themselves, and two code points of one
   * category share one instance, so categories may be compared by identity.
   *
   * @param codePoint The code point to classify.
   * @return The category, or {@code null} when no mapping covers the code point.
   */
  Category categoryOf(int codePoint) {
    if (codePoint <= Character.MAX_VALUE) {
      return bmp[codePoint];
    }
    int low = 0;
    int high = rangeStart.length - 1;
    while (low <= high) {
      final int middle = (low + high) >>> 1;
      if (codePoint < rangeStart[middle]) {
        high = middle - 1;
      } else if (codePoint > rangeEnd[middle]) {
        low = middle + 1;
      } else {
        return rangeCategory[middle];
      }
    }
    return null;
  }

  private static final String CHARACTER_DEFINITION_FILE = "char.def";

  /**
   * Collects {@code char.def} mappings in file order and builds a
   * {@link CategoryTable}, giving a later mapping precedence over an earlier one that
   * covers the same code point, which is what direct indexing does for the BMP.
   */
  static final class Builder {

    private final String[] bmp = new String[Character.MAX_VALUE + 1];
    private final List<int[]> bounds = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    /**
     * Records one inclusive code point range's category.
     *
     * @param from The first code point of the range.
     * @param to The last code point of the range, inclusive.
     * @param category The category name to give the range. Must not be {@code null}.
     */
    void map(int from, int to, String category) {
      for (int c = from; c <= Math.min(to, Character.MAX_VALUE); c++) {
        bmp[c] = category;
      }
      if (to > Character.MAX_VALUE) {
        bounds.add(new int[] {Math.max(from, Character.MAX_VALUE + 1), to});
        names.add(category);
      }
    }

    /**
     * Builds the lookup table from the recorded mappings.
     *
     * @param categories The categories the {@code char.def} category section defined,
     *                   keyed by name.
     * @return The table. Not {@code null}.
     * @throws IOException Thrown if a mapping names a category that was not defined.
     */
    CategoryTable build(Map<String, Category> categories) throws IOException {
      // Cut the supplementary ranges at every boundary they introduce, so that each
      // resulting elementary interval is covered by a single winning range and the
      // table stays sorted and non-overlapping for binary search.
      final int[] edges = new int[bounds.size() * 2];
      for (int i = 0; i < bounds.size(); i++) {
        edges[i * 2] = bounds.get(i)[0];
        edges[i * 2 + 1] = bounds.get(i)[1] + 1;
      }
      Arrays.sort(edges);
      final List<int[]> intervals = new ArrayList<>();
      final List<String> winners = new ArrayList<>();
      for (int i = 0; i < edges.length - 1; i++) {
        if (edges[i] == edges[i + 1]) {
          continue;
        }
        final String winner = lastCovering(edges[i]);
        if (winner == null) {
          continue;
        }
        final int previous = intervals.size() - 1;
        if (previous >= 0 && intervals.get(previous)[1] == edges[i] - 1
            && winners.get(previous).equals(winner)) {
          intervals.get(previous)[1] = edges[i + 1] - 1;
        } else {
          intervals.add(new int[] {edges[i], edges[i + 1] - 1});
          winners.add(winner);
        }
      }
      final int[] starts = new int[intervals.size()];
      final int[] ends = new int[intervals.size()];
      for (int i = 0; i < intervals.size(); i++) {
        starts[i] = intervals.get(i)[0];
        ends[i] = intervals.get(i)[1];
      }
      final Category[] resolvedBmp = new Category[bmp.length];
      for (int c = 0; c < bmp.length; c++) {
        if (bmp[c] != null) {
          resolvedBmp[c] = resolve(bmp[c], categories, c);
        }
      }
      final Category[] resolvedRanges = new Category[winners.size()];
      for (int i = 0; i < winners.size(); i++) {
        resolvedRanges[i] = resolve(winners.get(i), categories, starts[i]);
      }
      return new CategoryTable(resolvedBmp, starts, ends, resolvedRanges);
    }

    /**
     * Resolves a mapped category name against the defined categories. A mapping to an
     * undefined category fails at load and names the offending code point.
     *
     * @param name The category name a mapping line gave.
     * @param categories The defined categories, keyed by name.
     * @param codePoint A code point the mapping covers, for the error message.
     * @return The resolved category. Not {@code null}.
     * @throws IOException Thrown if no category of that name was defined.
     */
    private Category resolve(String name, Map<String, Category> categories,
        int codePoint) throws IOException {
      final Category category = categories.get(name);
      if (category == null) {
        throw new IOException(String.format(
            CHARACTER_DEFINITION_FILE + " maps U+%04X to the undefined category %s",
            codePoint, name));
      }
      return category;
    }

    /**
     * Finds the category of the last recorded range covering a code point.
     *
     * @param codePoint The code point to look up.
     * @return The category name, or {@code null} when no recorded range covers it.
     */
    private String lastCovering(int codePoint) {
      for (int i = bounds.size() - 1; i >= 0; i--) {
        final int[] range = bounds.get(i);
        if (codePoint >= range[0] && codePoint <= range[1]) {
          return names.get(i);
        }
      }
      return null;
    }
  }
}
