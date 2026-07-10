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
 *
 * <p>This mirrors the reference implementation's normalizer semantics rule for rule, since parity
 * of both the normalized bytes and the offset map is what the tests assert.</p>
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
  }

  /** The normalized bytes plus the normalized-byte to original-byte offset map. */
  record Normalized(byte[] bytes, int[] normToOrig) {
  }

  // One normalization step: `consumed` input bytes produced `data[from, to)`. The data array is
  // the input itself (pass-through), the replacement blob, or the replacement character.
  private record Chunk(byte[] data, int from, int to, int consumed) {

    boolean isSingleSpace() {
      return to - from == 1 && data[from] == ' ';
    }
  }

  /**
   * Normalizes UTF-8 input.
   *
   * @param input The well-formed UTF-8 bytes to normalize; must not be null.
   * @return The normalized bytes with the offset map; {@code normToOrig.length} is always
   *     {@code bytes.length + 1}.
   */
  Normalized normalize(byte[] input) {
    final ByteBuilder normalized = new ByteBuilder(input.length + (input.length >> 1) + 4);
    final IntBuilder normToOrig = new IntBuilder(input.length + (input.length >> 1) + 5);

    int from = 0;
    int consumed = 0;

    // Ignores heading whitespace.
    if (removeExtraWhitespaces) {
      while (from < input.length) {
        final Chunk p = normalizePrefix(input, from);
        if (!p.isSingleSpace()) {
          break;
        }
        from += p.consumed();
        consumed += p.consumed();
      }
    }

    // All input was whitespace.
    if (from >= input.length) {
      return new Normalized(new byte[0], new int[] {consumed});
    }

    final byte[] spaceSymbol = escapeWhitespaces ? SPACE_SYMBOL : new byte[] {' '};

    if (!treatWhitespaceAsSuffix && addDummyPrefix) {
      appendSpace(normalized, normToOrig, spaceSymbol, consumed);
    }

    boolean isPrevSpace = removeExtraWhitespaces;
    while (from < input.length) {
      final Chunk p = normalizePrefix(input, from);
      int spFrom = p.from();
      final int spTo = p.to();
      final byte[] spData = p.data();

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

      consumed += p.consumed();
      from += p.consumed();
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
    return new Normalized(normalized.toArray(), normToOrig.toArray());
  }

  private static void appendSpace(ByteBuilder normalized, IntBuilder normToOrig,
                                  byte[] spaceSymbol, int consumed) {
    normalized.append(spaceSymbol, 0, spaceSymbol.length);
    for (int i = 0; i < spaceSymbol.length; i++) {
      normToOrig.append(consumed);
    }
  }

  // Normalizes the longest applicable prefix of input[from, ...): a user-defined symbol passes
  // through raw, otherwise the longest character-map rule applies, otherwise one code point
  // passes through raw (or becomes U+FFFD when the lead byte is malformed).
  private Chunk normalizePrefix(byte[] input, int from) {
    if (userDefinedMatcher != null) {
      final int matched = longestUserDefinedMatch(input, from);
      if (matched > 0) {
        return new Chunk(input, from, from + matched, matched);
      }
    }

    if (trie != null) {
      final long match = trie.longestPrefixMatch(input, from, input.length);
      if (match >= 0) {
        final int value = (int) (match >>> 32);
        final int length = (int) (match & 0xFFFFFFFFL);
        final int replacementFrom = replacementsFrom + value;
        if (replacementFrom < blob.length) {
          int replacementTo = replacementFrom;
          while (blob[replacementTo] != 0) {
            replacementTo++;
          }
          return new Chunk(blob, replacementFrom, replacementTo, length);
        }
      }
    }

    final int charLength = Math.min(utf8Length(input[from]), input.length - from);
    if (isMalformed(input, from, charLength)) {
      return new Chunk(REPLACEMENT_CHAR, 0, REPLACEMENT_CHAR.length, 1);
    }
    return new Chunk(input, from, from + charLength, charLength);
  }

  private int longestUserDefinedMatch(byte[] input, int from) {
    int node = userDefinedMatcher.root();
    int longest = 0;
    for (int i = from; i < input.length; i++) {
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

  // The byte length of a UTF-8 sequence by its lead byte, as the reference implementation
  // computes it: trail and malformed lead bytes report one byte.
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

  // Checks a single code point for well-formedness: correct trail-byte count and no unpaired
  // surrogate or out-of-range value. The public tokenizer API encodes its own well-formed UTF-8,
  // so this only guards direct byte-level use.
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
