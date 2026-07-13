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
package opennlp.subword.sentencepiece;

/**
 * The model-embedded text normalizer of a SentencePiece model, operating in UTF-8 byte space.
 *
 * <p>Normalization applies the model's precompiled character map (leftmost-longest replacement
 * rules over UTF-8 prefixes), collapses and trims whitespace, optionally prepends the
 * word-boundary marker, and escapes spaces to U+2581. Alongside the normalized bytes it produces
 * {@code normToOrig}, mapping every normalized byte to the offset of the original byte chunk it
 * was derived from, with one trailing entry for the end position; that map is what lets every
 * downstream piece report an exact span of the caller's text.</p>
 */
final class SentencePieceNormalizer {

  // U+2581 LOWER ONE EIGHTH BLOCK in UTF-8, the escaped form of a space.
  static final byte[] SPACE_SYMBOL = {(byte) 0xE2, (byte) 0x96, (byte) 0x81};

  // U+FFFD REPLACEMENT CHARACTER in UTF-8, emitted for a malformed byte.
  private static final byte[] REPLACEMENT_CHAR = {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD};

  private final DoubleArrayTrie trie;
  private final byte[] blob;
  private final int replacementsFrom;
  private final boolean addDummyPrefix;
  private final boolean removeExtraWhitespaces;
  private final boolean escapeWhitespaces;
  private final boolean treatWhitespaceAsSuffix;
  private final PieceTrie userDefinedMatcher;
  // For each possible first byte, whether any character-map rule or user-defined symbol starts
  // with it. A clear bit proves normalizePrefix would pass the byte through raw, which lets the
  // scan skip the whole prefix machinery for plain ASCII text.
  private final boolean[] ruleLead = new boolean[256];

  /**
   * Instantiates the normalizer.
   *
   * @param precompiledCharsMap     The serialized character map; empty when the model has none.
   * @param addDummyPrefix          Whether a word-boundary marker is prepended.
   * @param removeExtraWhitespaces  Whether leading, trailing, and repeated whitespace collapses.
   * @param escapeWhitespaces       Whether spaces become U+2581.
   * @param treatWhitespaceAsSuffix Whether the dummy marker is appended instead of prepended.
   * @param userDefinedMatcher      Longest-match trie over user-defined symbols that must pass
   *                                through normalization untouched, or null when the model
   *                                defines none.
   * @throws IllegalArgumentException Thrown if the character map is structurally invalid.
   */
  SentencePieceNormalizer(byte[] precompiledCharsMap, boolean addDummyPrefix,
                          boolean removeExtraWhitespaces, boolean escapeWhitespaces,
                          boolean treatWhitespaceAsSuffix, PieceTrie userDefinedMatcher) {
    if (precompiledCharsMap.length == 0) {
      trie = null;
      blob = null;
      replacementsFrom = 0;
    } else {
      // Layout: <trie size, 4-byte little-endian><double-array trie><replacement blob>.
      if (precompiledCharsMap.length <= 4) {
        throw new IllegalArgumentException("The precompiled character map is truncated.");
      }
      final long trieSize = (precompiledCharsMap[0] & 0xFFL)
          | (precompiledCharsMap[1] & 0xFFL) << 8
          | (precompiledCharsMap[2] & 0xFFL) << 16
          | (precompiledCharsMap[3] & 0xFFL) << 24;
      if (trieSize >= precompiledCharsMap.length - 4) {
        throw new IllegalArgumentException(
            "The precompiled character map declares a trie of " + trieSize
                + " bytes but only " + (precompiledCharsMap.length - 4) + " bytes follow.");
      }
      if (trieSize < 1024 || (trieSize & 0x3FF) != 0) {
        throw new IllegalArgumentException(
            "The precompiled character map trie size " + trieSize
                + " is not a positive multiple of 1024.");
      }
      if (precompiledCharsMap[precompiledCharsMap.length - 1] != 0) {
        throw new IllegalArgumentException(
            "The precompiled character map replacement block is not null-terminated.");
      }
      trie = new DoubleArrayTrie(precompiledCharsMap, 4, (int) trieSize);
      blob = precompiledCharsMap;
      replacementsFrom = 4 + (int) trieSize;
    }
    this.addDummyPrefix = addDummyPrefix;
    this.removeExtraWhitespaces = removeExtraWhitespaces;
    this.escapeWhitespaces = escapeWhitespaces;
    this.treatWhitespaceAsSuffix = treatWhitespaceAsSuffix;
    this.userDefinedMatcher = userDefinedMatcher;
    for (int b = 0; b < 256; b++) {
      final boolean charsMapLead = trie != null && trie.hasTransitionFromRoot(b);
      final boolean userDefinedLead = userDefinedMatcher != null
          && userDefinedMatcher.step(userDefinedMatcher.root(), (byte) b) != PieceTrie.DEAD;
      ruleLead[b] = charsMapLead || userDefinedLead;
    }
  }

  /**
   * The normalized bytes plus the normalized-byte to original-byte offset map. The arrays are
   * builder-backed and may be oversized; {@code length} bytes are valid, and the offset map
   * holds {@code length + 1} entries.
   */
  record Normalized(byte[] bytes, int length, int[] normToOrig) {
  }

  /**
   * One normalization step: {@code consumed} input bytes produced {@code data[from, to)}. The data
   * array is the input itself (pass-through), the replacement blob, or the replacement character.
   * A single mutable scratch is refilled per chunk so the scan allocates nothing per code point.
   */
  private static final class Chunk {

    private byte[] data;
    private int from;
    private int to;
    private int consumed;

    /** {@return whether this chunk is exactly one ASCII space byte} */
    boolean isSingleSpace() {
      return to - from == 1 && data[from] == ' ';
    }
  }

  /**
   * Normalizes UTF-8 input.
   *
   * @param input       The buffer holding well-formed UTF-8 bytes; must not be null.
   * @param inputLength The number of valid bytes in {@code input}.
   * @return The normalized bytes with the offset map; the arrays are builder-backed, valid for
   *     {@code length} bytes and {@code length + 1} map entries.
   */
  Normalized normalize(byte[] input, int inputLength) {
    final ByteBuilder normalized = new ByteBuilder(inputLength + (inputLength >> 1) + 4);
    final IntBuilder normToOrig = new IntBuilder(inputLength + (inputLength >> 1) + 5);
    final Chunk chunk = new Chunk();

    int from = 0;
    int consumed = 0;

    // Ignores heading whitespace.
    if (removeExtraWhitespaces) {
      while (from < inputLength) {
        normalizePrefix(input, inputLength, from, chunk);
        if (!chunk.isSingleSpace()) {
          break;
        }
        from += chunk.consumed;
        consumed += chunk.consumed;
      }
    }

    // All input was whitespace.
    if (from >= inputLength) {
      normToOrig.append(consumed);
      return new Normalized(normalized.array(), 0, normToOrig.array());
    }

    final byte[] spaceSymbol = escapeWhitespaces ? SPACE_SYMBOL : SINGLE_SPACE;

    if (!treatWhitespaceAsSuffix && addDummyPrefix) {
      appendSpace(normalized, normToOrig, spaceSymbol, consumed);
    }

    boolean isPrevSpace = removeExtraWhitespaces;
    while (from < inputLength) {
      final int lead = input[from] & 0xFF;
      // Fast path: an ASCII byte no rule starts with passes through raw; the chunk would be
      // the byte itself, no leading-space stripping applies, and it does not end in a space.
      if (lead < 0x80 && lead != ' ' && !ruleLead[lead]) {
        normalized.append(input[from]);
        normToOrig.append(consumed);
        consumed++;
        from++;
        isPrevSpace = false;
        continue;
      }

      normalizePrefix(input, inputLength, from, chunk);
      int spFrom = chunk.from;
      final int spTo = chunk.to;
      final byte[] spData = chunk.data;

      // Removes heading spaces in the chunk if the previous chunk ended with whitespace.
      while (isPrevSpace && spFrom < spTo && spData[spFrom] == ' ') {
        spFrom++;
      }

      if (spFrom < spTo) {
        for (int n = spFrom; n < spTo; n++) {
          if (spData[n] == ' ') {
            appendSpace(normalized, normToOrig, spaceSymbol, consumed);
          } else {
            normalized.append(spData[n]);
            normToOrig.append(consumed);
          }
        }
        isPrevSpace = spData[spTo - 1] == ' ';
      }

      consumed += chunk.consumed;
      from += chunk.consumed;
      if (!removeExtraWhitespaces) {
        isPrevSpace = false;
      }
    }

    // Ignores trailing whitespace.
    if (removeExtraWhitespaces) {
      while (normalized.endsWith(spaceSymbol)) {
        final int length = normalized.length() - spaceSymbol.length;
        consumed = normToOrig.get(length);
        normalized.truncate(length);
        normToOrig.truncate(length);
      }
    }

    if (treatWhitespaceAsSuffix && addDummyPrefix) {
      appendSpace(normalized, normToOrig, spaceSymbol, consumed);
    }

    normToOrig.append(consumed);
    if (normToOrig.length() != normalized.length() + 1) {
      throw new IllegalStateException("The offset map has " + normToOrig.length()
          + " entries for " + normalized.length() + " normalized bytes.");
    }
    return new Normalized(normalized.array(), normalized.length(), normToOrig.array());
  }

  private static final byte[] SINGLE_SPACE = {' '};

  private static void appendSpace(ByteBuilder normalized, IntBuilder normToOrig,
                                  byte[] spaceSymbol, int consumed) {
    normalized.append(spaceSymbol, 0, spaceSymbol.length);
    for (int i = 0; i < spaceSymbol.length; i++) {
      normToOrig.append(consumed);
    }
  }

  /**
   * Fills the scratch with the normalized form of the longest applicable prefix of
   * {@code input[from, inputLength)}: a user-defined symbol passes through raw, otherwise the
   * longest character-map rule applies, otherwise one code point passes through raw (or becomes
   * U+FFFD when the lead byte is malformed).
   *
   * @param input       The UTF-8 input buffer.
   * @param inputLength The number of valid bytes in {@code input}.
   * @param from        The offset to normalize from.
   * @param chunk       The scratch to fill.
   */
  private void normalizePrefix(byte[] input, int inputLength, int from, Chunk chunk) {
    if (userDefinedMatcher != null) {
      final int matched = longestUserDefinedMatch(input, inputLength, from);
      if (matched > 0) {
        chunk.data = input;
        chunk.from = from;
        chunk.to = from + matched;
        chunk.consumed = matched;
        return;
      }
    }

    if (trie != null) {
      final long match = trie.longestPrefixMatch(input, from, inputLength);
      if (match >= 0) {
        final int value = (int) (match >>> 32);
        final int length = (int) (match & 0xFFFFFFFFL);
        final int replacementFrom = replacementsFrom + value;
        if (replacementFrom < blob.length) {
          int replacementTo = replacementFrom;
          while (blob[replacementTo] != 0) {
            replacementTo++;
          }
          chunk.data = blob;
          chunk.from = replacementFrom;
          chunk.to = replacementTo;
          chunk.consumed = length;
          return;
        }
      }
    }

    final int charLength = Math.min(utf8Length(input[from]), inputLength - from);
    if (isMalformed(input, from, charLength)) {
      chunk.data = REPLACEMENT_CHAR;
      chunk.from = 0;
      chunk.to = REPLACEMENT_CHAR.length;
      chunk.consumed = 1;
      return;
    }
    chunk.data = input;
    chunk.from = from;
    chunk.to = from + charLength;
    chunk.consumed = charLength;
  }

  /**
   * Returns the byte length of the longest user-defined symbol that is a prefix of
   * {@code input[from, inputLength)}.
   *
   * @param input       The UTF-8 input buffer.
   * @param inputLength The number of valid bytes in {@code input}.
   * @param from        The offset to match from.
   * @return The matched length in bytes, or zero when no user-defined symbol matches.
   */
  private int longestUserDefinedMatch(byte[] input, int inputLength, int from) {
    int node = userDefinedMatcher.root();
    int longest = 0;
    for (int i = from; i < inputLength; i++) {
      node = userDefinedMatcher.step(node, input[i]);
      if (node == PieceTrie.DEAD) {
        break;
      }
      if (userDefinedMatcher.value(node) >= 0) {
        longest = i - from + 1;
      }
    }
    return longest;
  }

  /**
   * Returns the byte length of a UTF-8 sequence from its lead byte; trail and malformed lead bytes
   * report one byte.
   *
   * @param lead The lead byte.
   * @return The sequence length in bytes, from one to four.
   */
  static int utf8Length(byte lead) {
    final int high = (lead & 0xFF) >>> 4;
    if (high < 0xC) {
      return 1;
    }
    return switch (high) {
      case 0xC, 0xD -> 2;
      case 0xE -> 3;
      default -> 4;
    };
  }

  /**
   * Checks a single code point for well-formedness: correct trail-byte count and no unpaired
   * surrogate or out-of-range value.
   *
   * @param input  The UTF-8 input buffer.
   * @param from   The offset of the lead byte.
   * @param length The candidate sequence length.
   * @return {@code true} when the sequence is malformed.
   */
  private static boolean isMalformed(byte[] input, int from, int length) {
    if ((input[from] & 0x80) == 0) {
      return false;
    }
    if ((input[from] & 0xC0) == 0x80 || length < utf8Length(input[from])) {
      return true;
    }
    for (int i = from + 1; i < from + length; i++) {
      if ((input[i] & 0xC0) != 0x80) {
        return true;
      }
    }
    final int codePoint = codePointAt(input, from, length);
    return codePoint < 0 || (codePoint >= 0xD800 && codePoint <= 0xDFFF) || codePoint > 0x10FFFF
        || length != minimalUtf8Length(codePoint);
  }

  /**
   * Decodes the code point of a UTF-8 sequence of the given length.
   *
   * @param input  The UTF-8 input buffer.
   * @param from   The offset of the lead byte.
   * @param length The sequence length in bytes, from one to four.
   * @return The decoded code point.
   */
  private static int codePointAt(byte[] input, int from, int length) {
    return switch (length) {
      case 1 -> input[from] & 0x7F;
      case 2 -> (input[from] & 0x1F) << 6 | (input[from + 1] & 0x3F);
      case 3 -> (input[from] & 0x0F) << 12 | (input[from + 1] & 0x3F) << 6
          | (input[from + 2] & 0x3F);
      default -> (input[from] & 0x07) << 18 | (input[from + 1] & 0x3F) << 12
          | (input[from + 2] & 0x3F) << 6 | (input[from + 3] & 0x3F);
    };
  }

  /**
   * Returns the number of bytes the shortest UTF-8 encoding of a code point uses, which detects
   * overlong encodings.
   *
   * @param codePoint The code point.
   * @return The minimal encoding length in bytes, from one to four.
   */
  private static int minimalUtf8Length(int codePoint) {
    if (codePoint < 0x80) {
      return 1;
    }
    if (codePoint < 0x800) {
      return 2;
    }
    if (codePoint < 0x10000) {
      return 3;
    }
    return 4;
  }
}
