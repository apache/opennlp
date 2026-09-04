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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import opennlp.tools.tokenize.lattice.MecabDictionary.Category;

/**
 * The {@code char.def} code point to category mappings over the Unicode code point
 * range. The first category on a mapping supplies the unknown-word settings. Each
 * listed category can keep a group running while following characters also list it.
 *
 * <p>The Basic Multilingual Plane is stored in a directly indexed array. The
 * supplementary planes are stored as a sorted, non-overlapping range table searched by
 * binary search, because dictionaries map them in a handful of large blocks.</p>
 */
final class CategoryTable {

  private final CategoryAssignment[] bmp;
  private final int[] rangeStart;
  private final int[] rangeEnd;
  private final CategoryAssignment[] rangeCategory;

  /**
   * Creates a table from resolved BMP entries and supplementary ranges.
   *
   * @param bmp The directly indexed BMP assignments.
   * @param rangeStart The inclusive starts of the supplementary ranges.
   * @param rangeEnd The inclusive upper bounds of the supplementary ranges.
   * @param rangeCategory The assignment for each supplementary range.
   */
  private CategoryTable(CategoryAssignment[] bmp, int[] rangeStart, int[] rangeEnd,
      CategoryAssignment[] rangeCategory) {
    this.bmp = bmp;
    this.rangeStart = rangeStart;
    this.rangeEnd = rangeEnd;
    this.rangeCategory = rangeCategory;
  }

  /**
   * Looks up the categories a {@code char.def} mapping gives a code point.
   *
   * @param codePoint The code point to classify.
   * @return The assignment, or {@code null} when no mapping covers the code point.
   */
  CategoryAssignment categoriesOf(int codePoint) {
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

  /**
   * The categories assigned to one code point, stored both in mapping order and as a
   * mask over the dictionary's dense category ids.
   */
  static final class CategoryAssignment {

    private final Category[] categories;
    private final int categoryMask;

    /**
     * Creates an assignment with the first entry as the primary category.
     *
     * @param categories The categories in mapping order. Must not be empty.
     * @throws IllegalArgumentException Thrown if {@code categories} is {@code null} or
     *         empty.
     */
    CategoryAssignment(Category[] categories) {
      if (categories == null || categories.length == 0) {
        throw new IllegalArgumentException("categories must not be null or empty");
      }
      this.categories = categories.clone();
      int mask = 0;
      for (final Category category : categories) {
        mask |= 1 << category.id();
      }
      this.categoryMask = mask;
    }

    /**
     * Returns the first category on the mapping.
     *
     * @return The category that supplies unknown-word settings.
     */
    Category primary() {
      return categories[0];
    }

    /**
     * Computes the run end after comparing this assignment with the next character.
     * MeCab's
     * <a href="https://github.com/taku910/mecab/blob/61b90ba6e669dc2d7d533d4a80d206f3b31d52b1/mecab/src/char_property.h#L37-L48">
     * {@code seekToOtherType}</a> replaces the current mask after each accepted
     * character, so successive assignments must overlap.
     *
     * @param next The next character's assignment, or {@code null} at the end of text.
     * @param nextRunEnd The run end calculated at the next character.
     * @param characterEnd The exclusive end of the current character.
     * @return {@code nextRunEnd} when the assignments intersect;
     *         {@code characterEnd} otherwise.
     */
    int continuedRunEnd(CategoryAssignment next, int nextRunEnd,
        int characterEnd) {
      return next != null && (categoryMask & next.categoryMask) != 0
          ? nextRunEnd : characterEnd;
    }
  }

  /**
   * Collects {@code char.def} mappings in file order and builds a
   * {@link CategoryTable}, giving a later mapping precedence over an earlier one that
   * covers the same code point, which is what direct indexing does for the BMP.
   */
  static final class Builder {

    /**
     * One mapping line retained for validation after all categories have been read.
     *
     * @param sourceStart The first code point on the mapping line, used in error
     *                    messages even if a later mapping replaces it.
     * @param categories The category names from the mapping, primary first.
     */
    private record Mapping(int sourceStart, String[] categories) {
    }

    private final String[][] bmp = new String[Character.MAX_VALUE + 1][];
    private final List<int[]> bounds = new ArrayList<>();
    private final List<String[]> names = new ArrayList<>();
    private final List<Mapping> mappings = new ArrayList<>();

    /**
     * Records one inclusive code point range's categories.
     *
     * @param from The first code point of the range.
     * @param to The last code point of the range, inclusive.
     * @param categories The category names to give the range, primary first. Must not
     *                   be {@code null} or empty.
     */
    void map(int from, int to, String[] categories) {
      // All positions written by this mapping store this array reference. build()
      // relies on array identity to resolve one CategoryAssignment per mapping line.
      mappings.add(new Mapping(from, categories));
      for (int c = from; c <= Math.min(to, Character.MAX_VALUE); c++) {
        bmp[c] = categories;
      }
      if (to > Character.MAX_VALUE) {
        bounds.add(new int[] {Math.max(from, Character.MAX_VALUE + 1), to});
        names.add(categories);
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
      final List<String[]> winners = new ArrayList<>();
      for (int i = 0; i < edges.length - 1; i++) {
        if (edges[i] == edges[i + 1]) {
          continue;
        }
        final String[] winner = lastCovering(edges[i]);
        if (winner == null) {
          continue;
        }
        final int previous = intervals.size() - 1;
        if (previous >= 0 && intervals.get(previous)[1] == edges[i] - 1
            && Arrays.equals(winners.get(previous), winner)) {
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
      final Map<String[], CategoryAssignment> resolvedAssignments =
          new IdentityHashMap<>();
      // Validate mappings in file order, including mappings replaced across their
      // ranges. This makes typo detection independent of range precedence.
      for (final Mapping mapping : mappings) {
        resolve(mapping.categories(), categories, resolvedAssignments,
            mapping.sourceStart());
      }
      final CategoryAssignment[] resolvedBmp = new CategoryAssignment[bmp.length];
      for (int c = 0; c < bmp.length; c++) {
        if (bmp[c] != null) {
          resolvedBmp[c] = resolve(bmp[c], categories, resolvedAssignments, c);
        }
      }
      final CategoryAssignment[] resolvedRanges = new CategoryAssignment[winners.size()];
      for (int i = 0; i < winners.size(); i++) {
        resolvedRanges[i] = resolve(winners.get(i), categories, resolvedAssignments,
            starts[i]);
      }
      return new CategoryTable(resolvedBmp, starts, ends, resolvedRanges);
    }

    /**
     * Resolves mapped category names using the defined categories. A mapping to an
     * undefined category fails at load and names the offending code point.
     *
     * @param names The category names on a mapping line, primary first.
     * @param categories The defined categories, keyed by name.
     * @param resolvedAssignments Previously resolved mapping lines, indexed by their
     *                            shared category-name arrays.
     * @param codePoint A code point the mapping covers, for the error message.
     * @return The resolved assignment. Not {@code null}.
     * @throws IOException Thrown if any named category was not defined.
     */
    private CategoryAssignment resolve(String[] names, Map<String, Category> categories,
        Map<String[], CategoryAssignment> resolvedAssignments, int codePoint)
        throws IOException {
      final CategoryAssignment cached = resolvedAssignments.get(names);
      if (cached != null) {
        return cached;
      }
      final Category[] resolved = new Category[names.length];
      for (int i = 0; i < names.length; i++) {
        resolved[i] = categories.get(names[i]);
        if (resolved[i] == null) {
          throw new IOException(String.format(Locale.ROOT,
              MecabDictionary.CHAR_DEF + " declaration at U+%04X names the"
                  + " undefined category %s", codePoint, names[i]));
        }
      }
      final CategoryAssignment assignment = new CategoryAssignment(resolved);
      resolvedAssignments.put(names, assignment);
      return assignment;
    }

    /**
     * Finds the category of the last recorded range covering a code point.
     *
     * @param codePoint The code point to look up.
     * @return The category names, or {@code null} when no stored range covers it.
     */
    private String[] lastCovering(int codePoint) {
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
