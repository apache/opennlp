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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The bundled, project-authored emoji/emoticon fold data ({@code emoji-emoticons.txt}) and the
 * sequence-substitution pass shared by {@link EmojiToEmoticonCharSequenceNormalizer} and
 * {@link EmoticonToEmojiCharSequenceNormalizer}.
 *
 * <p>Unlike the per-code-point folds behind {@link CharClass#substituteAligned}, both directions
 * here substitute code point <em>sequences</em>: an emoticon source such as {@code :-)} is three
 * characters, and an emoji-presentation source such as U+2764 U+FE0F is two code points that must
 * fold as one unit so no dangling variation selector is left behind. The scan is a single cursor
 * pass, longest match first at each position.</p>
 *
 * <p>In the pictographic direction the scan is sequence-aware: a mapped pictograph does not fold
 * when it participates in a larger ZWJ sequence (HEART ON FIRE, the family emoji) or when
 * followed by U+FE0E, which explicitly requests text presentation; both would otherwise corrupt
 * a distinct emoji or leave a dangling invisible selector. A trailing U+FE0F after any mapped
 * pictograph is absorbed into the fold, so the no-dangling-selector guarantee holds for every
 * mapped source, not only those with an explicit variation-selector row.</p>
 */
final class EmojiEmoticons {

  private static final String RESOURCE = "emoji-emoticons.txt";

  // The two direction tables, loaded lazily on first use and cached.
  private static volatile Tables tables;

  private EmojiEmoticons() {
  }

  /** One fold row: a source code point sequence and its replacement. */
  record Mapping(String source, String target) {
  }

  /**
   * One direction table, keyed by the first code point of the source sequence, candidates ordered
   * longest source first so a scan is longest-match. The first-code-point range bounds let the
   * scan skip the map lookup for the common code point that no source starts with.
   */
  record Direction(Map<Integer, List<Mapping>> table, int minFirst, int maxFirst) {

    List<Mapping> candidates(int codePoint) {
      if (codePoint < minFirst || codePoint > maxFirst) {
        return null;
      }
      return table.get(codePoint);
    }
  }

  /** The two direction tables. */
  record Tables(Direction emojiToEmoticon, Direction emoticonToEmoji) {
  }

  static Direction emojiToEmoticon() {
    return tables().emojiToEmoticon();
  }

  static Direction emoticonToEmoji() {
    return tables().emoticonToEmoji();
  }

  // Sequence-context code points of the pictographic direction.
  private static final int ZERO_WIDTH_JOINER = 0x200D;
  private static final int VARIATION_SELECTOR_TEXT = 0xFE0E;
  private static final int VARIATION_SELECTOR_EMOJI = 0xFE0F;

  /**
   * Applies the direction table in a single longest-match-first cursor pass.
   *
   * @param direction   A direction from {@link #emojiToEmoticon()} or {@link #emoticonToEmoji()}.
   * @param delimited   If {@code true}, a source matches only when delimited by the text boundary
   *                    or Unicode {@code White_Space} on both sides (the emoticon direction, where
   *                    the source sequences also occur inside ordinary text such as URLs). If
   *                    {@code false}, the pictographic sequence rules apply instead: no fold
   *                    inside a ZWJ sequence or before U+FE0E, and a trailing U+FE0F is absorbed.
   */
  static String substitute(CharSequence text, Direction direction, boolean delimited) {
    Objects.requireNonNull(text, "text");
    final StringBuilder out = new StringBuilder(text.length());
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      final long match = matchAt(text, i, direction.candidates(codePoint), delimited);
      if (match >= 0) {
        out.append(direction.candidates(codePoint).get((int) (match >>> 32)).target());
        i += (int) match;
      } else {
        out.appendCodePoint(codePoint);
        i += Character.charCount(codePoint);
      }
    }
    return out.toString();
  }

  /**
   * Like {@link #substitute} but also produces the {@link Alignment} back to the original text.
   * Each replaced source sequence, including an absorbed trailing U+FE0F, maps to its replacement
   * as one block.
   */
  static AlignedText substituteAligned(CharSequence text, Direction direction, boolean delimited) {
    Objects.requireNonNull(text, "text");
    final StringBuilder out = new StringBuilder(text.length());
    final Alignment.Builder alignment = new Alignment.Builder();
    final int length = text.length();
    int i = 0;
    while (i < length) {
      final int codePoint = Character.codePointAt(text, i);
      final long match = matchAt(text, i, direction.candidates(codePoint), delimited);
      if (match >= 0) {
        final String target = direction.candidates(codePoint).get((int) (match >>> 32)).target();
        final int consumed = (int) match;
        out.append(target);
        alignment.replace(consumed, target.length());
        i += consumed;
      } else {
        out.appendCodePoint(codePoint);
        final int charCount = Character.charCount(codePoint);
        alignment.equal(charCount);
        i += charCount;
      }
    }
    return new AlignedText(text, out.toString(), alignment.build(length));
  }

  // Returns the winning candidate as (candidateIndex << 32) | consumedChars, or -1 when nothing
  // folds here. Candidates are pre-sorted longest source first, so the first acceptable region
  // match wins. In the pictographic direction (delimited == false) a match is rejected when the
  // source adjoins a ZWJ sequence or is followed by U+FE0E, and a trailing U+FE0F joins the
  // consumed region.
  private static long matchAt(CharSequence text, int i, List<Mapping> candidates,
                              boolean delimited) {
    if (candidates == null || (delimited && !boundaryBefore(text, i))) {
      return -1;
    }
    if (!delimited && i > 0 && Character.codePointBefore(text, i) == ZERO_WIDTH_JOINER) {
      return -1;
    }
    for (int index = 0; index < candidates.size(); index++) {
      final Mapping candidate = candidates.get(index);
      int end = i + candidate.source().length();
      if (end > text.length() || !regionMatches(text, i, candidate.source())) {
        continue;
      }
      if (delimited) {
        if (boundaryAfter(text, end)) {
          return ((long) index << 32) | (end - i);
        }
        continue;
      }
      // Absorb one trailing emoji-presentation selector into the fold.
      if (end < text.length() && Character.codePointAt(text, end) == VARIATION_SELECTOR_EMOJI) {
        end++;
      }
      if (end < text.length()) {
        final int following = Character.codePointAt(text, end);
        if (following == ZERO_WIDTH_JOINER || following == VARIATION_SELECTOR_TEXT) {
          continue;
        }
      }
      return ((long) index << 32) | (end - i);
    }
    return -1;
  }

  private static boolean boundaryBefore(CharSequence text, int i) {
    return i == 0 || CharClass.whitespace().contains(Character.codePointBefore(text, i));
  }

  private static boolean boundaryAfter(CharSequence text, int end) {
    return end == text.length() || CharClass.whitespace().contains(Character.codePointAt(text, end));
  }

  private static boolean regionMatches(CharSequence text, int start, String source) {
    for (int k = 0; k < source.length(); k++) {
      if (text.charAt(start + k) != source.charAt(k)) {
        return false;
      }
    }
    return true;
  }

  private static Tables tables() {
    Tables t = tables;
    if (t == null) {
      synchronized (EmojiEmoticons.class) {
        t = tables;
        if (t == null) {
          t = load();
          tables = t;
        }
      }
    }
    return t;
  }

  private static Tables load() {
    try (InputStream in = EmojiEmoticons.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing emoji/emoticon fold data resource: " + RESOURCE);
      }
      return parse(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Unable to read emoji/emoticon fold data resource "
          + RESOURCE, e);
    }
  }

  // Package-private so the malformed-data handling can be exercised without the bundled resource.
  // Parses rows of "source ; target ; fold_type ; standard ; unicode_version ; notes" with
  // space-separated hexadecimal code points; '#' starts a comment line. EMOJI rows load into the
  // emoji-to-emoticon table, EMOTICON rows into the emoticon-to-emoji table.
  static Tables parse(InputStream in) throws IOException {
    final Map<Integer, List<Mapping>> emojiToEmoticon = new HashMap<>();
    final Map<Integer, List<Mapping>> emoticonToEmoji = new HashMap<>();
    try (BufferedReader reader =
             new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        final String content = line.strip();
        if (content.isEmpty() || content.startsWith("#")) {
          continue;
        }
        // Bounded split: the notes column is free text that may itself contain ';' (for example
        // the winking emoticon), so only the first five separators are structural.
        final String[] fields = content.split(";", 6);
        if (fields.length != 6) {
          throw new IllegalArgumentException("Malformed emoji/emoticon fold data in " + RESOURCE
              + " at line " + lineNumber + ": expected 6 fields, got " + fields.length
              + " in: " + content);
        }
        final String source = decode(fields[0], lineNumber, content);
        final String target = decode(fields[1], lineNumber, content);
        final String foldType = fields[2].strip();
        final Map<Integer, List<Mapping>> table = switch (foldType) {
          case "EMOJI" -> emojiToEmoticon;
          case "EMOTICON" -> emoticonToEmoji;
          default -> throw new IllegalArgumentException("Malformed emoji/emoticon fold data in "
              + RESOURCE + " at line " + lineNumber + ": unrecognized fold_type '" + foldType
              + "' in: " + content);
        };
        final int firstCodePoint = source.codePointAt(0);
        final List<Mapping> candidates =
            table.computeIfAbsent(firstCodePoint, k -> new ArrayList<>());
        for (final Mapping existing : candidates) {
          if (existing.source().equals(source)) {
            throw new IllegalArgumentException("Malformed emoji/emoticon fold data in " + RESOURCE
                + " at line " + lineNumber + ": duplicate " + foldType + " source in: " + content);
          }
        }
        candidates.add(new Mapping(source, target));
      }
    }
    return new Tables(direction(emojiToEmoticon), direction(emoticonToEmoji));
  }

  // Longest source first, so the scan in matchAt is longest-match by construction; the
  // first-code-point bounds feed the no-match short circuit.
  private static Direction direction(Map<Integer, List<Mapping>> table) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (final Map.Entry<Integer, List<Mapping>> entry : table.entrySet()) {
      min = Math.min(min, entry.getKey());
      max = Math.max(max, entry.getKey());
      entry.getValue().sort(
          Comparator.comparingInt((Mapping m) -> m.source().length()).reversed());
    }
    return new Direction(Map.copyOf(table), min, max);
  }

  private static String decode(String hexCodePoints, int lineNumber, String content) {
    final String stripped = hexCodePoints.strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("Malformed emoji/emoticon fold data in " + RESOURCE
          + " at line " + lineNumber + ": empty code point sequence in: " + content);
    }
    try {
      final StringBuilder decoded = new StringBuilder();
      for (final String hex : stripped.split(" ")) {
        decoded.appendCodePoint(Integer.parseInt(hex, 16));
      }
      return decoded.toString();
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Malformed emoji/emoticon fold data in " + RESOURCE
          + " at line " + lineNumber + ": " + content, e);
    }
  }
}
