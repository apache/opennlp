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

/**
 * The bundled emoji/emoticon fold tables ({@code emoji-emoticons.txt}) and the sequence
 * substitution shared by {@link EmojiToEmoticonCharSequenceNormalizer} and
 * {@link EmoticonToEmojiCharSequenceNormalizer}. Both directions substitute code point
 * <em>sequences</em>, longest match first at each position. In the emoticon direction a source
 * matches only when delimited by the text boundary or whitespace on both sides; in the emoji
 * direction a mapped pictograph inside a ZWJ sequence or before U+FE0E is left untouched, and a
 * trailing U+FE0F is absorbed into the fold.
 */
final class EmojiEmoticons {

  private static final String RESOURCE = "emoji-emoticons.txt";

  /** Starts a comment line in {@code emoji-emoticons.txt}. */
  private static final String COMMENT_PREFIX = "#";

  /**
   * Field separator in {@code emoji-emoticons.txt}
   * ({@code source ; target ; fold_type ; standard ; unicode_version ; notes}).
   */
  private static final String FIELD_SEPARATOR = ";";

  /**
   * Separates hex code points inside a source or target field. The bundled table format uses
   * ASCII space ({@code U+0020}), not a general whitespace class.
   */
  private static final String MAPPING_CODE_POINT_SEPARATOR = " ";

  private static final int ZERO_WIDTH_JOINER = 0x200D;
  private static final int VARIATION_SELECTOR_TEXT = 0xFE0E;
  private static final int VARIATION_SELECTOR_EMOJI = 0xFE0F;

  private static final EmojiEmoticons INSTANCE = new EmojiEmoticons(loadBundled());

  private final Direction emojiToEmoticon;
  private final Direction emoticonToEmoji;

  private EmojiEmoticons(Tables tables) {
    this.emojiToEmoticon = tables.emojiToEmoticon();
    this.emoticonToEmoji = tables.emoticonToEmoji();
  }

  /**
   * {@return the shared instance over the bundled {@code emoji-emoticons.txt} data} The tables are
   * loaded once when this class initializes.
   */
  static EmojiEmoticons getInstance() {
    return INSTANCE;
  }

  /**
   * One fold row: a source code point sequence and its replacement.
   *
   * @param source the code point sequence to replace.
   * @param target the replacement sequence.
   */
  record Mapping(String source, String target) {
  }

  /**
   * One direction table, keyed by the first code point of the source sequence, candidates ordered
   * longest source first so a scan is longest-match. The first-code-point range bounds let the
   * scan skip the map lookup for the common code point that no source starts with.
   *
   * @param table    the candidate mappings keyed by first source code point.
   * @param minFirst the smallest first code point of any source.
   * @param maxFirst the largest first code point of any source.
   */
  record Direction(Map<Integer, List<Mapping>> table, int minFirst, int maxFirst) {

    /**
     * {@return the candidate mappings whose source starts with {@code codePoint}, longest source
     * first, or {@code null} if no source starts with it}
     *
     * @param codePoint the code point at the current scan position.
     */
    List<Mapping> candidates(int codePoint) {
      if (codePoint < minFirst || codePoint > maxFirst) {
        return null;
      }
      return table.get(codePoint);
    }
  }

  /**
   * The two direction tables produced by {@link #parse(InputStream)}.
   *
   * @param emojiToEmoticon the pictograph-to-emoticon direction.
   * @param emoticonToEmoji the emoticon-to-pictograph direction.
   */
  record Tables(Direction emojiToEmoticon, Direction emoticonToEmoji) {
  }

  /**
   * Folds mapped pictographs in {@code text} to their ASCII emoticons.
   *
   * @param text the text to fold. Must not be {@code null}.
   * @return the folded text.
   */
  String emojiToEmoticon(CharSequence text) {
    return substitute(text, emojiToEmoticon, false);
  }

  /**
   * Folds mapped pictographs in {@code text} to their ASCII emoticons, producing the
   * {@link Alignment} back to the original text. Each replaced source sequence, including an
   * absorbed trailing U+FE0F, maps to its replacement as one block.
   *
   * @param text the text to fold. Must not be {@code null}.
   * @return the folded text with its alignment.
   */
  AlignedText emojiToEmoticonAligned(CharSequence text) {
    return substituteAligned(text, emojiToEmoticon, false);
  }

  /**
   * Folds whitespace-delimited ASCII emoticons in {@code text} to their pictographs.
   *
   * @param text the text to fold. Must not be {@code null}.
   * @return the folded text.
   */
  String emoticonToEmoji(CharSequence text) {
    return substitute(text, emoticonToEmoji, true);
  }

  /**
   * Folds whitespace-delimited ASCII emoticons in {@code text} to their pictographs, producing
   * the {@link Alignment} back to the original text.
   *
   * @param text the text to fold. Must not be {@code null}.
   * @return the folded text with its alignment.
   */
  AlignedText emoticonToEmojiAligned(CharSequence text) {
    return substituteAligned(text, emoticonToEmoji, true);
  }

  /**
   * Applies the direction table in a single longest-match-first cursor pass.
   *
   * @param text      the text to fold.
   * @param direction the direction table to apply.
   * @param delimited if {@code true}, a source matches only when delimited by the text boundary
   *                  or Unicode {@code White_Space} on both sides (the emoticon direction). If
   *                  {@code false}, the pictographic sequence rules apply instead: no fold inside
   *                  a ZWJ sequence or before U+FE0E, and a trailing U+FE0F is absorbed.
   * @return the folded text.
   */
  private static String substitute(CharSequence text, Direction direction, boolean delimited) {
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
   * Like {@link #substitute(CharSequence, Direction, boolean)} but also produces the
   * {@link Alignment} back to the original text. Each replaced source sequence, including an
   * absorbed trailing U+FE0F, maps to its replacement as one block.
   *
   * @param text      the text to fold.
   * @param direction the direction table to apply.
   * @param delimited see {@link #substitute(CharSequence, Direction, boolean)}.
   * @return the folded text with its alignment.
   */
  private static AlignedText substituteAligned(CharSequence text, Direction direction,
                                               boolean delimited) {
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

  /**
   * Finds the winning candidate at position {@code i}. Candidates are pre-sorted longest source
   * first, so the first acceptable region match wins. In the pictographic direction
   * ({@code delimited == false}) a match is rejected when the source adjoins a ZWJ sequence or is
   * followed by U+FE0E, and a trailing U+FE0F joins the consumed region.
   *
   * @param text       the text being scanned.
   * @param i          the scan position.
   * @param candidates the candidates whose source starts with the code point at {@code i}, or
   *                   {@code null} if there are none.
   * @param delimited  whether the whitespace-delimited boundary rule applies.
   * @return the winning candidate encoded as {@code (candidateIndex << 32) | consumedChars}, or
   *     {@code -1} when nothing folds here.
   */
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

  /**
   * {@return whether position {@code i} is preceded by the text boundary or whitespace}
   *
   * @param text the text being scanned.
   * @param i    the scan position.
   */
  private static boolean boundaryBefore(CharSequence text, int i) {
    return i == 0 || CharClass.whitespace().contains(Character.codePointBefore(text, i));
  }

  /**
   * {@return whether position {@code end} is the text boundary or followed by whitespace}
   *
   * @param text the text being scanned.
   * @param end  the position just past a candidate match.
   */
  private static boolean boundaryAfter(CharSequence text, int end) {
    return end == text.length() || CharClass.whitespace().contains(Character.codePointAt(text, end));
  }

  /**
   * {@return whether {@code text} contains exactly {@code source} starting at {@code start}}
   *
   * @param text   the text being scanned.
   * @param start  the position to compare from. The caller guarantees
   *               {@code start + source.length() <= text.length()}.
   * @param source the sequence to compare against.
   */
  private static boolean regionMatches(CharSequence text, int start, String source) {
    for (int k = 0; k < source.length(); k++) {
      if (text.charAt(start + k) != source.charAt(k)) {
        return false;
      }
    }
    return true;
  }

  /**
   * {@return the tables parsed from the bundled {@code emoji-emoticons.txt} resource}
   *
   * @throws IllegalStateException if the resource is missing.
   * @throws UncheckedIOException if the resource cannot be read.
   */
  private static Tables loadBundled() {
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

  /**
   * Parses rows of {@code source ; target ; fold_type ; standard ; unicode_version ; notes} with
   * space-separated hexadecimal code points; {@code '#'} starts a comment line. {@code EMOJI} rows
   * load into the emoji-to-emoticon table, {@code EMOTICON} rows into the emoticon-to-emoji
   * table. Package-private so the malformed-data handling can be exercised without the bundled
   * resource.
   *
   * @param in the stream to parse. Must not be {@code null}.
   * @return the two direction tables.
   * @throws IOException if the stream cannot be read.
   * @throws IllegalArgumentException if the data is malformed.
   */
  static Tables parse(InputStream in) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    final Map<Integer, List<Mapping>> emojiToEmoticon = new HashMap<>();
    final Map<Integer, List<Mapping>> emoticonToEmoji = new HashMap<>();
    try (BufferedReader reader =
             new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        final String content = line.strip();
        if (content.isEmpty() || content.startsWith(COMMENT_PREFIX)) {
          continue;
        }
        // The notes column is free text that may itself contain ';', so only the first five
        // separators are structural.
        final String[] fields = content.split(FIELD_SEPARATOR, 6);
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

  /**
   * Builds a {@link Direction} from a parsed table: sorts each candidate list longest source
   * first, so the scan in {@link #matchAt} is longest-match by construction, and computes the
   * first-code-point bounds that feed the no-match short circuit.
   *
   * @param table the mutable table produced by {@link #parse(InputStream)}.
   * @return the immutable direction.
   */
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

  /**
   * Decodes a space-separated hexadecimal code point sequence.
   *
   * @param hexCodePoints the field to decode.
   * @param lineNumber    the line number, for the error message.
   * @param content       the full line, for the error message.
   * @return the decoded sequence.
   * @throws IllegalArgumentException if the field is empty or not valid hexadecimal.
   */
  private static String decode(String hexCodePoints, int lineNumber, String content) {
    final String stripped = hexCodePoints.strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("Malformed emoji/emoticon fold data in " + RESOURCE
          + " at line " + lineNumber + ": empty code point sequence in: " + content);
    }
    try {
      final StringBuilder decoded = new StringBuilder();
      for (final String hex : stripped.split(MAPPING_CODE_POINT_SEPARATOR)) {
        decoded.appendCodePoint(Integer.parseInt(hex, 16));
      }
      return decoded.toString();
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Malformed emoji/emoticon fold data in " + RESOURCE
          + " at line " + lineNumber + ": " + content, e);
    }
  }
}
