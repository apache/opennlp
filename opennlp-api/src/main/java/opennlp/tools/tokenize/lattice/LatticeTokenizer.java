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

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.lattice.CategoryTable.CategoryAssignment;
import opennlp.tools.tokenize.lattice.MecabDictionary.Category;
import opennlp.tools.tokenize.lattice.MecabDictionary.WordEntry;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Dictionary-driven segmentation for languages written without spaces: a Viterbi
 * search over the word lattice of a {@link MecabDictionary}, minimizing the sum of
 * word costs and connection costs. This is the segmentation approach behind Japanese
 * and Korean morphological analysis. A single decoder serves both because the supplied
 * dictionary provides the language-specific data.
 *
 * <p>Unknown text is handled through the dictionary's character categories: where the
 * lexicon has no entry, or a category always invokes them, unknown-word candidates are
 * generated per category template, grouping runs of same-category characters when the
 * category requests it. A multi-category run continues while successive assignments
 * overlap. Whitespace cannot join or appear as a morpheme.
 * Every reported span is in original text coordinates.</p>
 *
 * <p>{@link #analyze(String)} returns full morphemes with their dictionary features;
 * the {@link Tokenizer} view reports just the surfaces and spans.</p>
 *
 * <p>The tokenizer reads only immutable dictionary state and is safe to share between
 * threads.</p>
 *
 * @since 3.0.0
 */
public final class LatticeTokenizer implements Tokenizer {

  /** The context id of the beginning and end of text. */
  private static final int BOUNDARY_CONTEXT = 0;

  private final MecabDictionary dictionary;

  /**
   * Initializes the tokenizer.
   *
   * @param dictionary The dictionary to segment with. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code dictionary} is {@code null}.
   */
  public LatticeTokenizer(MecabDictionary dictionary) {
    if (dictionary == null) {
      throw new IllegalArgumentException("dictionary must not be null");
    }
    this.dictionary = dictionary;
  }

  /**
   * One lattice node: a candidate morpheme with its best path cost so far. Nodes
   * ending at one position chain through {@link #nextEndingHere}.
   */
  private static final class Node {
    private final int start;
    private final int end;
    private final WordEntry entry;
    private final boolean unknown;
    private long pathCost = Long.MAX_VALUE;
    private Node previous;
    private Node nextEndingHere;

    /**
     * Creates one lattice candidate.
     *
     * @param start The candidate start in original text coordinates.
     * @param end The exclusive candidate end.
     * @param entry The dictionary entry.
     * @param unknown Whether unknown-word handling generated the candidate.
     */
    private Node(int start, int end, WordEntry entry, boolean unknown) {
      this.start = start;
      this.end = end;
      this.entry = entry;
      this.unknown = unknown;
    }
  }

  /**
   * Segments a text into morphemes with their dictionary features.
   *
   * @param text The text to segment. Must not be {@code null}.
   * @return The morphemes in text order, spans in original coordinates, whitespace
   *         omitted. Never {@code null}; empty for empty or all-whitespace input.
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   * @throws IllegalStateException Thrown if the dictionary offers no candidate at some
   *         position, which a {@code unk.def} without a {@code DEFAULT} template does.
   */
  public List<Morpheme> analyze(String text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    final List<Morpheme> morphemes = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      if (StringUtil.isWhitespace(text.charAt(start))) {
        start++;
        continue;
      }
      int end = start;
      while (end < text.length() && !StringUtil.isWhitespace(text.charAt(end))) {
        end++;
      }
      decode(text, start, end, morphemes);
      start = end;
    }
    return morphemes;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Reports the segmented surfaces, whitespace omitted.</p>
   *
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   * @throws IllegalStateException Thrown if the dictionary offers no candidate at some
   *         position; see {@link #analyze(String)}.
   */
  @Override
  public String[] tokenize(String text) {
    final List<Morpheme> morphemes = analyze(text);
    final String[] tokens = new String[morphemes.size()];
    for (int i = 0; i < tokens.length; i++) {
      tokens[i] = morphemes.get(i).surface();
    }
    return tokens;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Reports the segmented spans in original text coordinates, whitespace omitted.</p>
   *
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   * @throws IllegalStateException Thrown if the dictionary offers no candidate at some
   *         position; see {@link #analyze(String)}.
   */
  @Override
  public Span[] tokenizePos(String text) {
    final List<Morpheme> morphemes = analyze(text);
    final Span[] spans = new Span[morphemes.size()];
    for (int i = 0; i < spans.length; i++) {
      spans[i] = morphemes.get(i).span();
    }
    return spans;
  }

  /**
   * Runs the Viterbi search over one whitespace-free stretch of text.
   *
   * @param text The text being segmented.
   * @param from The stretch start.
   * @param to The exclusive stretch end.
   * @param morphemes Receives the cheapest path's morphemes, in text order.
   * @throws IllegalStateException Thrown if no path reaches the end of the stretch.
   */
  private void decode(String text, int from, int to, List<Morpheme> morphemes) {
    final int length = to - from;
    // Each element heads the chain of nodes ending at that position.
    final Node[] endingAt = new Node[length + 1];

    final CategoryAssignment[] categoryAt = new CategoryAssignment[length];
    final int[] runEndAt = new int[length];
    computeCategoryRuns(text, from, to, categoryAt, runEndAt);

    final List<Node> candidates = new ArrayList<>();
    for (int i = 0; i < length; i++) {
      if (i > 0 && endingAt[i] == null) {
        continue;
      }
      candidates.clear();
      candidates(text, from, to, i, categoryAt[i], runEndAt[i], candidates);
      for (final Node candidate : candidates) {
        relax(candidate, i == 0 ? null : endingAt[i]);
        if (candidate.pathCost < Long.MAX_VALUE) {
          final int end = candidate.end - from;
          candidate.nextEndingHere = endingAt[end];
          endingAt[end] = candidate;
        }
      }
    }

    Node best = null;
    long bestTotal = Long.MAX_VALUE;
    for (Node node = endingAt[length]; node != null; node = node.nextEndingHere) {
      final long total = node.pathCost
          + dictionary.connectionCost(node.entry.rightId(), BOUNDARY_CONTEXT);
      if (best == null || total < bestTotal) {
        best = node;
        bestTotal = total;
      }
    }
    if (best == null) {
      throw new IllegalStateException(
          "no segmentation path for \"" + text.subSequence(from, to) + "\"");
    }

    final List<Morpheme> reversed = new ArrayList<>();
    for (Node node = best; node != null; node = node.previous) {
      reversed.add(new Morpheme(new Span(node.start, node.end),
          text.substring(node.start, node.end), node.entry.features(), node.unknown));
    }
    for (int i = reversed.size() - 1; i >= 0; i--) {
      morphemes.add(reversed.get(i));
    }
  }

  /**
   * Connects a candidate to the cheapest predecessor ending where it starts.
   *
   * @param candidate The node to give a path cost and a predecessor.
   * @param predecessors The head of the chain of nodes ending where the candidate
   *                     starts, or {@code null} when it starts at the stretch start.
   */
  private void relax(Node candidate, Node predecessors) {
    if (predecessors == null) {
      candidate.pathCost = candidate.entry.cost()
          + dictionary.connectionCost(BOUNDARY_CONTEXT, candidate.entry.leftId());
      return;
    }
    for (Node predecessor = predecessors; predecessor != null;
        predecessor = predecessor.nextEndingHere) {
      final long total = predecessor.pathCost
          + dictionary.connectionCost(predecessor.entry.rightId(), candidate.entry.leftId())
          + candidate.entry.cost();
      if (total < candidate.pathCost) {
        candidate.pathCost = total;
        candidate.previous = predecessor;
      }
    }
  }

  /**
   * Computes the per-position categories and connected-category run end for one
   * stretch, in one right-to-left pass over the code points. Positions inside a
   * surrogate sequence have a {@code null} assignment; candidates do not start there.
   *
   * @param text The text being segmented.
   * @param from The stretch start.
   * @param to The exclusive stretch end.
   * @param categoryAt Receives each position's categories, indexed by {@code
   *                   position - from}.
   * @param runEndAt Receives each position's exclusive connected-category run end,
   *                 indexed the same way.
   */
  private void computeCategoryRuns(String text, int from, int to,
      CategoryAssignment[] categoryAt, int[] runEndAt) {
    CategoryAssignment next = null;
    int nextRunEnd = to;
    for (int position = to; position > from; ) {
      final int codePoint = text.codePointBefore(position);
      position -= Character.charCount(codePoint);
      final int index = position - from;
      categoryAt[index] = dictionary.categoriesOf(codePoint);
      runEndAt[index] = categoryAt[index].continuedRunEnd(next, nextRunEnd,
          position + Character.charCount(codePoint));
      next = categoryAt[index];
      nextRunEnd = runEndAt[index];
    }
  }

  /**
   * Gathers lexicon matches and unknown-word candidates starting at one position.
   *
   * @param text The text being segmented.
   * @param from The stretch start.
   * @param to The exclusive stretch end, which no candidate may reach past.
   * @param offset The candidate start, relative to {@code from}.
   * @param positionCategories The categories of that position, or {@code null} for a
   *                           position inside a surrogate sequence.
   * @param positionRunEnd The exclusive end of the overlapping-category run starting
   *                       there. Used only when {@code positionCategories} is not
   *                       {@code null}.
   * @param candidates Receives the candidates. Must be empty on entry.
   * @throws IllegalStateException Thrown if neither the lexicon, the position's
   *         category, nor the {@code DEFAULT} template offers a candidate.
   */
  private void candidates(String text, int from, int to, int offset,
      CategoryAssignment positionCategories, int positionRunEnd, List<Node> candidates) {
    final int position = from + offset;
    dictionary.prefixMatches(text, position, to, (length, entries) -> {
      for (final WordEntry entry : entries) {
        candidates.add(new Node(position, position + length, entry, false));
      }
    });
    final boolean lexiconMatch = !candidates.isEmpty();

    final int codePoint = text.codePointAt(position);
    final Category category;
    final int runEnd;
    if (positionCategories == null) {
      // Only a lexicon surface ending inside a surrogate pair can make such a
      // position reachable; classify the stray code unit on the spot so the lattice
      // stays connected.
      category = dictionary.categoryOf(codePoint);
      runEnd = position + Character.charCount(codePoint);
    } else {
      category = positionCategories.primary();
      runEnd = positionRunEnd;
    }
    if (!lexiconMatch || category.invoke()) {
      final List<WordEntry> templates = dictionary.unknownEntries(category.name());
      if (templates != null) {
        addUnknown(candidates, text, position, runEnd, category, templates);
      }
    }
    if (candidates.isEmpty()) {
      // A category with no grouped or fixed-length candidate still provides one
      // character. Incomplete dictionaries fall back to the DEFAULT template.
      List<WordEntry> fallback = dictionary.unknownEntries(category.name());
      if (fallback == null) {
        fallback = dictionary.unknownEntries(MecabDictionary.DEFAULT_CATEGORY);
      }
      if (fallback != null) {
        for (final WordEntry entry : fallback) {
          candidates.add(
              new Node(position, position + Character.charCount(codePoint), entry, true));
        }
      }
    }
    if (candidates.isEmpty()) {
      throw new IllegalStateException("dictionary provides no candidate at position "
          + position + "; unk.def lacks a DEFAULT template");
    }
  }

  /**
   * Emits unknown-word candidates per the category's grouping and length settings.
   *
   * <p>Candidates remain inside a run connected by overlapping category assignments.
   * Lengths count code points, not UTF-16 code units.</p>
   *
   * @param candidates Receives the candidates.
   * @param text The text being segmented.
   * @param position The position the candidates start at.
   * @param runEnd The exclusive end of the connected-category run starting at
   *               {@code position}.
   * @param category The category of that run.
   * @param templates The category's unknown-word templates.
   */
  private void addUnknown(List<Node> candidates, String text, int position,
      int runEnd, Category category, List<WordEntry> templates) {
    if (category.group()) {
      for (final WordEntry entry : templates) {
        candidates.add(new Node(position, runEnd, entry, true));
      }
    }
    final int lengths = category.length();
    int end = position;
    for (int length = 1; length <= lengths && end < runEnd; length++) {
      end += Character.charCount(text.codePointAt(end));
      if (category.group() && end == runEnd) {
        // This length coincides with the grouped run emitted above; skip the duplicate.
        continue;
      }
      for (final WordEntry entry : templates) {
        candidates.add(new Node(position, end, entry, true));
      }
    }
  }
}
