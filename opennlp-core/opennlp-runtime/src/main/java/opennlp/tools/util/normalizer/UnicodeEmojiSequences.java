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
package opennlp.tools.util.normalizer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds complete emoji at a position of a text, from the bundled inventory of Unicode Emoji 17.0
 * sequences.
 *
 * <p>A sequence is <em>fully-qualified</em> in the sense of
 * <a href="https://www.unicode.org/reports/tr51/">UTS #51</a> when every code point that needs
 * an emoji presentation selector has one: the {@code fully-qualified} lines of
 * {@code emoji-test.txt}, which cover single emoji, emoji with U+FE0F, flags, keycaps, skin tone
 * modifier sequences, ZWJ sequences and tag sequences. The inventory holds those sequences in a
 * trie, plus the {@code Emoji_Component} ranges of {@code emoji-data.txt}: the joiners,
 * modifiers, selectors, flag letters and tags that occur inside sequences. A component on its
 * own is never an emoji, but a stray one next to an emoji marks a malformed candidate that is
 * kept as it is.</p>
 */
final class UnicodeEmojiSequences {

  private static final String RESOURCE = "EmojiSequences.txt";

  /** Prefix of a record holding one fully-qualified sequence. */
  private static final String SEQUENCE_RECORD = "S;";

  /** Prefix of a record holding one {@code Emoji_Component} code point range. */
  private static final String COMPONENT_RECORD = "C;";

  /** First character of a comment line of the data file. */
  private static final char COMMENT = '#';

  /**
   * U+FE0E asks for text presentation. It has no {@code Emoji_Component} property but it binds
   * to the symbol before it the same way U+FE0F does, so it counts as a structural component.
   */
  private static final int VARIATION_SELECTOR_TEXT = 0xFE0E;

  /** U+FE0F asks for emoji presentation. */
  private static final int VARIATION_SELECTOR_EMOJI = 0xFE0F;

  /**
   * The bases of the keycap sequences: {@code #}, {@code *} and the ASCII digits. They have the
   * {@code Emoji_Component} property, but as ordinary text characters they must never make a
   * neighboring emoji a malformed candidate, so they are excluded from the structural
   * components.
   */
  private static final CodePointSet KEYCAP_BASES =
      CodePointSet.of('#', '*').union(CodePointSet.ofRange('0', '9'));

  // Volatile so the lazily loaded instance is safely published: the double-checked accessor
  // reads the field once, and a fully built trie becomes visible to every thread that observes
  // the non-null reference.
  private static volatile UnicodeEmojiSequences instance;

  private final Node root;
  private final int[][] componentRanges;
  private final int sequenceCount;

  /**
   * Every code point a candidate can start with: the first code point of a sequence or a
   * structural component. One bit test rejects nearly every position of ordinary text before
   * the trie is touched.
   */
  private final BitSet candidateStarts;

  private UnicodeEmojiSequences(Node root, int[][] componentRanges, int sequenceCount) {
    this.root = root;
    this.componentRanges = componentRanges;
    this.sequenceCount = sequenceCount;
    this.candidateStarts = new BitSet();
    for (int codePoint : root.children.keySet()) {
      candidateStarts.set(codePoint);
    }
    for (int[] range : componentRanges) {
      candidateStarts.set(range[0], range[1] + 1);
    }
    candidateStarts.set(VARIATION_SELECTOR_TEXT);
  }

  /**
   * {@return the matcher built from the bundled data, loaded on first use}
   *
   * @throws IllegalStateException Thrown if the bundled data resource is missing.
   * @throws UncheckedIOException Thrown if the bundled data resource cannot be read.
   * @throws IllegalArgumentException Thrown if the bundled data is malformed.
   */
  static UnicodeEmojiSequences getInstance() {
    UnicodeEmojiSequences sequences = instance;
    if (sequences == null) {
      synchronized (UnicodeEmojiSequences.class) {
        sequences = instance;
        if (sequences == null) {
          sequences = load();
          instance = sequences;
        }
      }
    }
    return sequences;
  }

  /** {@return the number of sequence records that were loaded} */
  int sequenceCount() {
    return sequenceCount;
  }

  /**
   * Finds the emoji candidate that starts at a position.
   *
   * <p>The candidate is a run of complete sequences, or a malformed one: an emoji with a stray
   * component such as a trailing joiner or an extra modifier, a leading component connected to
   * an emoji, or a prefix of a sequence that continues with a component. A stray component
   * connects only to the complete sequence right before it and to the one right after it;
   * complete sequences that merely touch the malformed part are not included, so the caller
   * removes them and sees the malformed candidate at a later call. One U+FE0F right after a
   * complete sequence is part of it.</p>
   *
   * @param text The text to look into. Must not be {@code null}.
   * @param start The index the candidate must start at.
   * @return {@code null} if no candidate starts at {@code start}; otherwise a candidate that is
   *     {@linkplain Candidate#valid() valid} when it consists of complete sequences only, and
   *     invalid when it holds a stray component or an incomplete sequence, with
   *     {@link Candidate#end()} as the index after it in both cases.
   */
  Candidate candidateAt(CharSequence text, int start) {
    int first = Character.codePointAt(text, start);
    if (!candidateStarts.get(first)) {
      return null;
    }
    Walk walk = walk(text, start);
    int end = walk.matchEnd();
    boolean valid = end >= 0;
    if (!valid) {
      int prefixEnd = walk.prefixEnd();
      if (!KEYCAP_BASES.contains(first) && prefixEnd > start && prefixEnd < text.length()
          && isStructuralComponent(Character.codePointAt(text, prefixEnd))) {
        end = prefixEnd;
      } else if (isStructuralComponent(first)) {
        end = start + Character.charCount(first);
      } else {
        return null;
      }
    }
    // The start of the most recent complete sequence of the run, and whether the item before
    // the current position is a stray component. A component connects only to the complete
    // sequence right before it and to the one right after it.
    int lastSequenceStart = start;
    boolean afterComponent = !valid;
    int position = end;
    while (position < text.length()) {
      int next = match(text, position);
      if (next >= 0) {
        if (!valid && !afterComponent) {
          break;
        }
        lastSequenceStart = position;
        position = next;
        afterComponent = false;
        continue;
      }
      int codePoint = Character.codePointAt(text, position);
      if (!isStructuralComponent(codePoint)) {
        break;
      }
      if (valid && lastSequenceStart > start) {
        // Only the last complete sequence is connected to the stray component: the run ends
        // before it, and the next call at that sequence yields the malformed candidate.
        return new Candidate(lastSequenceStart, true);
      }
      valid = false;
      afterComponent = true;
      position += Character.charCount(codePoint);
    }
    return new Candidate(position, valid);
  }

  /**
   * {@return the index after the longest complete sequence starting at {@code start}, or
   * {@code -1} if none starts there}
   *
   * @param text The text to look into. Must not be {@code null}.
   * @param start The index the sequence must start at.
   */
  private int match(CharSequence text, int start) {
    return walk(text, start).matchEnd();
  }

  /**
   * Walks the trie along the text from a position.
   *
   * @param text The text to look into. Must not be {@code null}.
   * @param start The index the walk starts at.
   * @return The index after the longest complete sequence, or {@code -1} if none, and the
   *     index after the longest prefix of any sequence, which is {@code start} if none.
   */
  private Walk walk(CharSequence text, int start) {
    Node node = root;
    int longest = -1;
    int i = start;
    while (i < text.length()) {
      int codePoint = Character.codePointAt(text, i);
      Node child = node.children.get(codePoint);
      if (child == null) {
        break;
      }
      node = child;
      i += Character.charCount(codePoint);
      if (node.terminal) {
        longest = i;
      }
    }
    if (longest >= 0 && longest < text.length()
        && Character.codePointAt(text, longest) == VARIATION_SELECTOR_EMOJI) {
      // One redundant emoji presentation selector after a complete sequence belongs to it.
      longest++;
    }
    return new Walk(longest, i);
  }

  /**
   * {@return whether a code point is a joiner, modifier, selector, flag letter or tag that
   * occurs inside sequences} These are the {@code Emoji_Component} ranges plus U+FE0E, without
   * the keycap bases.
   *
   * @param codePoint The code point to test.
   */
  private boolean isStructuralComponent(int codePoint) {
    if (KEYCAP_BASES.contains(codePoint)) {
      return false;
    }
    if (codePoint == VARIATION_SELECTOR_TEXT) {
      return true;
    }
    int low = 0;
    int high = componentRanges.length - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      int[] range = componentRanges[middle];
      if (codePoint < range[0]) {
        high = middle - 1;
      } else if (codePoint > range[1]) {
        low = middle + 1;
      } else {
        return true;
      }
    }
    return false;
  }

  /**
   * Loads the bundled data resource.
   *
   * @return The matcher.
   * @throws IllegalStateException Thrown if the bundled data resource is missing.
   * @throws UncheckedIOException Thrown if the bundled data resource cannot be read.
   * @throws IllegalArgumentException Thrown if the bundled data is malformed.
   */
  private static UnicodeEmojiSequences load() {
    try (InputStream in = UnicodeEmojiSequences.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing emoji sequence data resource: " + RESOURCE);
      }
      return parse(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to read emoji sequence data resource " + RESOURCE, e);
    }
  }

  /**
   * Parses {@code S;} sequence and {@code C;} component range records into a matcher.
   * Package-visible so the malformed-data handling can be exercised without the bundled
   * resource.
   *
   * @param in The records to read, one per line; {@code #} lines are comments.
   * @return The matcher.
   * @throws IOException Thrown if reading {@code in} fails.
   * @throws IllegalArgumentException Thrown if a record is malformed.
   */
  static UnicodeEmojiSequences parse(InputStream in) throws IOException {
    Node root = new Node();
    int sequences = 0;
    List<int[]> ranges = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(in, StandardCharsets.US_ASCII))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank() || line.charAt(0) == COMMENT) {
          continue;
        }
        try {
          if (line.startsWith(SEQUENCE_RECORD)) {
            addSequence(root, line.substring(SEQUENCE_RECORD.length()));
            sequences++;
          } else if (line.startsWith(COMPONENT_RECORD)) {
            ranges.add(HexCodePoints.parseRange(line.substring(COMPONENT_RECORD.length())));
          } else {
            throw new IllegalArgumentException("neither a comment nor a record");
          }
        } catch (IllegalArgumentException e) {
          // Fail loud naming the bad line, the same way the sibling loaders do.
          throw new IllegalArgumentException("Malformed emoji sequence data in " + RESOURCE
              + " at line " + lineNumber + ": " + line, e);
        }
      }
    }
    if (sequences == 0) {
      throw new IllegalArgumentException("No " + SEQUENCE_RECORD + " sequence record in "
          + RESOURCE);
    }
    if (ranges.isEmpty()) {
      throw new IllegalArgumentException("No " + COMPONENT_RECORD + " component range record in "
          + RESOURCE);
    }
    return new UnicodeEmojiSequences(root, ranges.toArray(int[][]::new), sequences);
  }

  /**
   * Adds one sequence to the trie.
   *
   * @param root The root of the trie. Must not be {@code null}.
   * @param sequence The hex code points of the sequence, separated by single spaces.
   * @throws IllegalArgumentException Thrown if a code point is malformed.
   */
  private static void addSequence(Node root, String sequence) {
    Node node = root;
    int tokenStart = 0;
    for (int i = 0; i <= sequence.length(); i++) {
      if (i == sequence.length() || sequence.charAt(i) == HexCodePoints.SEQUENCE_SEPARATOR) {
        int codePoint = HexCodePoints.parseCodePoint(sequence, tokenStart, i);
        node = node.children.computeIfAbsent(codePoint, ignored -> new Node());
        tokenStart = i + 1;
      }
    }
    node.terminal = true;
  }

  /**
   * What {@link #candidateAt(CharSequence, int)} found at a position.
   *
   * @param end The index after the candidate.
   * @param valid {@code true} if the candidate consists of complete sequences only and is to be
   *     replaced; {@code false} if it holds a stray component or an incomplete sequence and is
   *     to be kept as it is.
   */
  record Candidate(int end, boolean valid) {
  }

  /**
   * The result of one trie walk.
   *
   * @param matchEnd The index after the longest complete sequence, or {@code -1} if none.
   * @param prefixEnd The index after the longest prefix of any sequence.
   */
  private record Walk(int matchEnd, int prefixEnd) {
  }

  /** A trie node: the code points that may follow, and whether a sequence ends here. */
  private static final class Node {
    private final Map<Integer, Node> children = new HashMap<>();
    private boolean terminal;
  }
}
