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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Frequency-driven segmentation for Chinese and similar scripts: a Viterbi search that
 * maximizes the summed log-probability of the words in a user-supplied frequency
 * lexicon, with unlisted characters falling back to single-character words. This is the
 * unigram model behind common Chinese segmenters; it carries no connection costs, so it
 * is lighter than the {@link LatticeTokenizer} and fits lexicons that list only words
 * and counts.
 *
 * <p>The lexicon format is one entry per line: the word, its count, and optionally a
 * tag, separated by whitespace. The lexicon file is user-supplied; no lexicon data is
 * bundled. Every reported span is in original text coordinates.</p>
 *
 * <p>Instances are immutable and safe to share between threads.</p>
 *
 * @since 3.0.0
 */
public class UnigramSegmenter implements Tokenizer {

  /** The log-probability charged to a character the lexicon does not know. */
  private final double unknownLogProbability;

  private final WordTrie trie;

  /**
   * One immutable trie node: children are a sorted character array with a parallel
   * node array, found by binary search, so a descent never boxes a {@link Character}.
   */
  private static final class WordTrie {

    private final char[] keys;
    private final WordTrie[] nodes;
    private final double logProbability;

    private WordTrie(char[] keys, WordTrie[] nodes, double logProbability) {
      this.keys = keys;
      this.nodes = nodes;
      this.logProbability = logProbability;
    }

    /**
     * Descends one character.
     *
     * @param c The next surface character.
     * @return The child node, or {@code null} when no surface continues with {@code c}.
     */
    private WordTrie child(char c) {
      final int index = Arrays.binarySearch(keys, c);
      return index >= 0 ? nodes[index] : null;
    }
  }

  /** One mutable trie node during construction, copied into a {@link WordTrie}. */
  private static final class WordTrieBuilder {

    private final Map<Character, WordTrieBuilder> children = new HashMap<>();
    private double logProbability = Double.NaN;

    private WordTrie freeze() {
      final char[] keys = new char[children.size()];
      int i = 0;
      for (final Character key : children.keySet()) {
        keys[i++] = key;
      }
      Arrays.sort(keys);
      final WordTrie[] nodes = new WordTrie[keys.length];
      for (int k = 0; k < keys.length; k++) {
        nodes[k] = children.get(keys[k]).freeze();
      }
      return new WordTrie(keys, nodes, logProbability);
    }
  }

  private UnigramSegmenter(WordTrie trie, double unknownLogProbability) {
    this.trie = trie;
    this.unknownLogProbability = unknownLogProbability;
  }

  /**
   * Loads a frequency lexicon encoded in UTF-8.
   *
   * @param lexicon The lexicon file. Must not be {@code null}.
   * @return The segmenter. Never {@code null}.
   * @throws IOException Thrown if reading fails or the lexicon is empty or malformed.
   * @throws IllegalArgumentException Thrown if {@code lexicon} is {@code null}.
   */
  public static UnigramSegmenter load(Path lexicon) throws IOException {
    return load(lexicon, StandardCharsets.UTF_8);
  }

  /**
   * Loads a frequency lexicon.
   *
   * @param lexicon The lexicon file: one word, its count, and an optional tag per
   *                line. Must not be {@code null}.
   * @param charset The lexicon encoding. Must not be {@code null}.
   * @return The segmenter. Never {@code null}.
   * @throws IOException Thrown if reading fails or the lexicon is empty or malformed.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static UnigramSegmenter load(Path lexicon, Charset charset) throws IOException {
    if (lexicon == null) {
      throw new IllegalArgumentException("lexicon must not be null");
    }
    if (charset == null) {
      throw new IllegalArgumentException("charset must not be null");
    }
    try (InputStream in = Files.newInputStream(lexicon)) {
      return load(in, charset);
    }
  }

  /**
   * Loads a frequency lexicon from a stream.
   *
   * @param lexiconStream The lexicon content. Must not be {@code null}. Not closed.
   * @param charset The lexicon encoding. Must not be {@code null}.
   * @return The segmenter. Never {@code null}.
   * @throws IOException Thrown if reading fails or the lexicon is empty or malformed.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static UnigramSegmenter load(InputStream lexiconStream, Charset charset)
      throws IOException {
    if (lexiconStream == null) {
      throw new IllegalArgumentException("lexiconStream must not be null");
    }
    if (charset == null) {
      throw new IllegalArgumentException("charset must not be null");
    }
    final Map<String, Long> counts = new HashMap<>();
    long total = 0;
    final BufferedReader reader =
        new BufferedReader(new InputStreamReader(lexiconStream, charset));
    int lineNumber = 0;
    String raw;
    while ((raw = reader.readLine()) != null) {
      lineNumber++;
      final String line = StringUtil.trimUnicodeWhitespace(raw);
      if (line.isEmpty()) {
        continue;
      }
      final int wordEnd = whitespaceIndex(line);
      if (wordEnd < 0) {
        throw new IOException("lexicon line " + lineNumber + " has no count");
      }
      final String word = line.substring(0, wordEnd);
      int countStart = wordEnd;
      while (countStart < line.length() && StringUtil.isWhitespace(line.charAt(countStart))) {
        countStart++;
      }
      int countEnd = countStart;
      while (countEnd < line.length() && !StringUtil.isWhitespace(line.charAt(countEnd))) {
        countEnd++;
      }
      final long count;
      try {
        count = Long.parseLong(line.substring(countStart, countEnd));
      } catch (NumberFormatException e) {
        throw new IOException("malformed count at lexicon line " + lineNumber, e);
      }
      if (count <= 0) {
        throw new IOException("count must be positive at lexicon line " + lineNumber);
      }
      counts.merge(word, count, Long::sum);
      total += count;
    }
    if (counts.isEmpty()) {
      throw new IOException("the lexicon lists no words");
    }

    final WordTrieBuilder root = new WordTrieBuilder();
    final double logTotal = Math.log(total);
    for (final Map.Entry<String, Long> entry : counts.entrySet()) {
      WordTrieBuilder node = root;
      final String word = entry.getKey();
      for (int c = 0; c < word.length(); c++) {
        node = node.children.computeIfAbsent(word.charAt(c), key -> new WordTrieBuilder());
      }
      node.logProbability = Math.log(entry.getValue()) - logTotal;
    }
    // Charge an unlisted character half of one count out of the total, which makes it
    // rarer than any listed word: every listed count is at least one.
    final double unknown = Math.log(0.5) - logTotal;
    return new UnigramSegmenter(root.freeze(), unknown);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Reports the segmented surfaces, whitespace omitted.</p>
   *
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  @Override
  public String[] tokenize(String text) {
    return Span.spansToStrings(tokenizePos(text), text);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Reports the segmented spans in original text coordinates, whitespace omitted.</p>
   *
   * @throws IllegalArgumentException Thrown if {@code text} is {@code null}.
   */
  @Override
  public Span[] tokenizePos(String text) {
    if (text == null) {
      throw new IllegalArgumentException("text must not be null");
    }
    final List<Span> spans = new ArrayList<>();
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
      decode(text, start, end, spans);
      start = end;
    }
    return spans.toArray(new Span[0]);
  }

  /**
   * Viterbi over word log-probabilities within one whitespace-free stretch.
   *
   * @param text The text being segmented.
   * @param from The stretch start.
   * @param to The exclusive stretch end.
   * @param spans Receives the best path's spans, in text order and in original text
   *              coordinates.
   */
  private void decode(String text, int from, int to, List<Span> spans) {
    final int length = to - from;
    final double[] best = new double[length + 1];
    final int[] previous = new int[length + 1];
    for (int i = 1; i <= length; i++) {
      best[i] = Double.NEGATIVE_INFINITY;
    }
    for (int i = 0; i < length; i++) {
      if (best[i] == Double.NEGATIVE_INFINITY) {
        continue;
      }
      // A single-character step at the unknown log-probability keeps every position
      // reachable even where no lexicon word matches. The step advances one code
      // point, never one code unit, so an unknown supplementary character is stepped
      // over whole and no span boundary can land between its surrogate halves.
      final int width = Character.charCount(text.codePointAt(from + i));
      final double fallback = best[i] + unknownLogProbability;
      if (i + width <= length && fallback > best[i + width]) {
        best[i + width] = fallback;
        previous[i + width] = i;
      }
      WordTrie node = trie;
      for (int j = from + i; j < to; j++) {
        node = node.child(text.charAt(j));
        if (node == null) {
          break;
        }
        if (!Double.isNaN(node.logProbability)) {
          final int end = j - from + 1;
          final double score = best[i] + node.logProbability;
          if (score > best[end]) {
            best[end] = score;
            previous[end] = i;
          }
        }
      }
    }
    final List<Span> reversed = new ArrayList<>();
    for (int end = length; end > 0; end = previous[end]) {
      reversed.add(new Span(from + previous[end], from + end));
    }
    for (int i = reversed.size() - 1; i >= 0; i--) {
      spans.add(reversed.get(i));
    }
  }

  /**
   * Finds the first whitespace character in a lexicon line.
   *
   * @param text The line to scan.
   * @return The index of the first whitespace character, or {@code -1} when the line
   *         contains none.
   */
  private static int whitespaceIndex(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (StringUtil.isWhitespace(text.charAt(i))) {
        return i;
      }
    }
    return -1;
  }
}
