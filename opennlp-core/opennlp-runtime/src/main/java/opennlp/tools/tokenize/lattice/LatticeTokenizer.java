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
import opennlp.tools.tokenize.lattice.MecabDictionary.Category;
import opennlp.tools.tokenize.lattice.MecabDictionary.WordEntry;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Dictionary-driven segmentation for languages written without spaces: a Viterbi
 * search over the word lattice of a {@link MecabDictionary}, minimizing the sum of
 * word costs and connection costs. This is the segmentation approach behind Japanese
 * and Korean morphological analysis; the same decoder serves both, since the language
 * lives entirely in the user-supplied dictionary.
 *
 * <p>Unknown text is handled through the dictionary's character categories: where the
 * lexicon has no entry, or a category always invokes them, unknown-word candidates are
 * generated per category template, grouping runs of same-category characters when the
 * category says so. Whitespace never joins a morpheme and is never reported as one.
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
public class LatticeTokenizer implements Tokenizer {

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

  /** One lattice node: a candidate morpheme with its best path cost so far. */
  private static final class Node {
    private final int start;
    private final int end;
    private final WordEntry entry;
    private final boolean unknown;
    private long pathCost = Long.MAX_VALUE;
    private Node previous;

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

  @Override
  public String[] tokenize(String s) {
    final List<Morpheme> morphemes = analyze(s);
    final String[] tokens = new String[morphemes.size()];
    for (int i = 0; i < tokens.length; i++) {
      tokens[i] = morphemes.get(i).surface();
    }
    return tokens;
  }

  @Override
  public Span[] tokenizePos(String s) {
    final List<Morpheme> morphemes = analyze(s);
    final Span[] spans = new Span[morphemes.size()];
    for (int i = 0; i < spans.length; i++) {
      spans[i] = morphemes.get(i).span();
    }
    return spans;
  }

  /** Runs the Viterbi search over one whitespace-free stretch of text. */
  private void decode(String text, int from, int to, List<Morpheme> morphemes) {
    final int length = to - from;
    final List<List<Node>> endingAt = new ArrayList<>(length + 1);
    for (int i = 0; i <= length; i++) {
      endingAt.add(new ArrayList<>());
    }
    final boolean[] reachable = new boolean[length + 1];
    reachable[0] = true;

    for (int i = 0; i < length; i++) {
      if (!reachable[i]) {
        continue;
      }
      final List<Node> candidates = candidates(text, from, to, i);
      for (final Node candidate : candidates) {
        relax(candidate, i == 0 ? null : endingAt.get(i));
        if (candidate.pathCost < Long.MAX_VALUE) {
          endingAt.get(candidate.end - from).add(candidate);
          reachable[candidate.end - from] = true;
        }
      }
    }

    Node best = null;
    for (final Node node : endingAt.get(length)) {
      final long total = node.pathCost
          + dictionary.connectionCost(node.entry.rightId(), BOUNDARY_CONTEXT);
      if (best == null || total < best.pathCost
          + dictionary.connectionCost(best.entry.rightId(), BOUNDARY_CONTEXT)) {
        best = node;
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

  /** Connects a candidate to the cheapest predecessor ending where it starts. */
  private void relax(Node candidate, List<Node> predecessors) {
    if (predecessors == null) {
      candidate.pathCost = candidate.entry.cost()
          + dictionary.connectionCost(BOUNDARY_CONTEXT, candidate.entry.leftId());
      return;
    }
    for (final Node predecessor : predecessors) {
      final long total = predecessor.pathCost
          + dictionary.connectionCost(predecessor.entry.rightId(), candidate.entry.leftId())
          + candidate.entry.cost();
      if (total < candidate.pathCost) {
        candidate.pathCost = total;
        candidate.previous = predecessor;
      }
    }
  }

  /** Gathers lexicon matches and unknown-word candidates starting at one position. */
  private List<Node> candidates(String text, int from, int to, int offset) {
    final int position = from + offset;
    final List<Node> candidates = new ArrayList<>();
    final int longest = Math.min(dictionary.maxSurfaceLength(), to - position);
    boolean lexiconMatch = false;
    for (int length = 1; length <= longest; length++) {
      final List<WordEntry> entries =
          dictionary.lookup(text.substring(position, position + length));
      if (entries == null) {
        continue;
      }
      lexiconMatch = true;
      for (final WordEntry entry : entries) {
        candidates.add(new Node(position, position + length, entry, false));
      }
    }

    final Category category = dictionary.categoryOf(text.charAt(position));
    if (!lexiconMatch || category.invoke()) {
      int run = position + 1;
      while (run < to
          && dictionary.categoryOf(text.charAt(run)).name().equals(category.name())) {
        run++;
      }
      final List<WordEntry> templates = dictionary.unknownEntries(category.name());
      if (templates != null) {
        addUnknown(candidates, position, run, to, category, templates);
      }
    }
    if (candidates.isEmpty()) {
      // no entry and no template: a single-character fallback keeps the lattice alive
      final List<WordEntry> fallback = dictionary.unknownEntries("DEFAULT");
      if (fallback != null) {
        for (final WordEntry entry : fallback) {
          candidates.add(new Node(position, position + 1, entry, true));
        }
      }
    }
    if (candidates.isEmpty()) {
      throw new IllegalStateException("dictionary provides no candidate at position "
          + position + "; unk.def lacks a DEFAULT template");
    }
    return candidates;
  }

  /** Emits unknown-word candidates per the category's grouping and length settings. */
  private static void addUnknown(List<Node> candidates, int position, int runEnd,
      int to, Category category, List<WordEntry> templates) {
    if (category.group()) {
      for (final WordEntry entry : templates) {
        candidates.add(new Node(position, runEnd, entry, true));
      }
    }
    final int lengths = category.length();
    for (int length = 1; length <= lengths && position + length <= to; length++) {
      if (category.group() && position + length == runEnd) {
        continue; // already emitted as the grouped run
      }
      for (final WordEntry entry : templates) {
        candidates.add(new Node(position, position + length, entry, true));
      }
    }
  }
}
