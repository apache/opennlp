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

import java.io.Serializable;

/**
 * Read-only lookup over a serialized Darts-clone double-array trie, the dictionary format
 * embedded in a SentencePiece model's precompiled character map.
 *
 * <p>Each unit is one little-endian 32-bit word encoding a label, an offset to the unit's
 * children, and a leaf flag; traversal XORs the offset with the next key byte. Only the longest
 * prefix match is needed here, so this walks the byte key once and remembers the last accepting
 * state. Out-of-range unit references, which a valid trie never produces, are rejected
 * rather than reading arbitrary memory.</p>
 *
 * <p>This class is an independent re-implementation of the reader side of that format, written
 * against the published double-array literature: the trie itself is Aoe's double-array, the unit
 * encoding is the compact static variant of Yata et al., and the bit-9 offset extension is the
 * two-kinds-of-offset scheme described by Kanda et al. Darts-clone, by the same Yata, is the
 * reference implementation of the format.</p>
 *
 * @see <a href="https://github.com/s-yata/darts-clone">Darts-clone</a>
 * @see <a href="https://doi.org/10.1109/32.31365">Aoe (1989): An Efficient Digital Search
 *     Algorithm by Using a Double-Array Structure</a>
 * @see <a href="https://doi.org/10.1016/j.ipm.2006.04.004">Yata et al. (2007): A compact static
 *     double-array keeping character codes</a>
 * @see <a href="https://doi.org/10.1002/spe.3190">Kanda et al. (2023): Engineering faster
 *     double-array Aho-Corasick automata</a>
 */
final class DoubleArrayTrie implements Serializable {

  private static final long serialVersionUID = -1572336116472261588L;

  // A non-leaf unit stores its transition label in the low 8 bits and the leaf flag in the sign
  // bit. Key bytes are in [0, 255] with the sign bit clear, so comparing (unit & this mask)
  // against a key byte both matches the label and rejects leaf units in one test.
  private static final int LEAF_FLAG_AND_LABEL_MASK = 0x800000FF;

  // A leaf unit stores the key's value in its low 31 bits; the sign bit is the leaf flag.
  private static final int LEAF_VALUE_MASK = 0x7FFFFFFF;

  // Bit 8 of a non-leaf unit marks that one of its children is a leaf holding this key's value.
  private static final int HAS_LEAF_BIT = 8;

  private final int[] units;

  /**
   * Wraps serialized trie units.
   *
   * @param data   The bytes holding the units; must not be null.
   * @param offset The offset of the first unit byte.
   * @param length The number of bytes; must be a positive multiple of four.
   * @throws IllegalArgumentException Thrown if {@code length} is not a positive multiple of four.
   */
  DoubleArrayTrie(byte[] data, int offset, int length) {
    if (length <= 0 || (length & 3) != 0) {
      throw new IllegalArgumentException(
          "The trie length " + length + " is not a positive multiple of four bytes.");
    }
    units = new int[length >> 2];
    for (int i = 0; i < units.length; i++) {
      final int base = offset + (i << 2);
      units[i] = (data[base] & 0xFF) | (data[base + 1] & 0xFF) << 8
          | (data[base + 2] & 0xFF) << 16 | (data[base + 3] & 0xFF) << 24;
    }
  }

  /**
   * Finds the longest key that is a prefix of {@code key[from, to)}.
   *
   * @param key  The byte key to match against; must not be null.
   * @param from The inclusive start of the query window.
   * @param to   The exclusive end of the query window.
   * @return {@code (value << 32) | matchedLength} for the longest match, or {@code -1} when no
   *     key matches. Values are non-negative, so the result is negative only on no-match.
   * @throws IllegalArgumentException Thrown if the trie data references a unit outside its
   *     bounds, indicating corrupt data.
   */
  long longestPrefixMatch(byte[] key, int from, int to) {
    // The JVM's own bounds checks guard the walk; the catch below translates an out-of-range unit
    // reference from corrupt data into a loud failure.
    final int[] u = units;
    try {
      long result = -1;
      int nodePos = 0;
      int unit = u[0];
      nodePos ^= offset(unit);
      for (int i = from; i < to; i++) {
        final int b = key[i] & 0xFF;
        nodePos ^= b;
        unit = u[nodePos];
        if ((unit & LEAF_FLAG_AND_LABEL_MASK) != b) {
          return result;
        }
        nodePos ^= offset(unit);
        if (((unit >>> HAS_LEAF_BIT) & 1) == 1) {
          final int value = u[nodePos] & LEAF_VALUE_MASK;
          result = ((long) value << 32) | (i - from + 1);
        }
      }
      return result;
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new IllegalArgumentException(
          "The trie references a unit outside its " + u.length + " units.", e);
    }
  }

  /**
   * Tests whether any key starts with the given byte, which is exactly whether the root has a
   * transition on it; used to precompute the first-byte gate of the normalizer scan.
   *
   * @param b The first key byte as an unsigned value.
   * @return {@code true} if some key starts with {@code b}.
   */
  boolean hasTransitionFromRoot(int b) {
    final int root = units[0];
    final int nodePos = offset(root) ^ b;
    if (nodePos < 0 || nodePos >= units.length) {
      return false;
    }
    return (units[nodePos] & LEAF_FLAG_AND_LABEL_MASK) == b;
  }

  /**
   * Returns the offset from a unit to its children, as encoded by Darts-clone: bits 10 to 30 hold
   * the raw offset, and bit 9 is an extension flag that scales it by 256 for far-away children.
   *
   * @param unit The unit word.
   * @return The child offset.
   */
  private static int offset(int unit) {
    final int raw = unit >>> 10;
    return (unit & 1 << 9) == 0 ? raw : raw << 8;
  }
}
