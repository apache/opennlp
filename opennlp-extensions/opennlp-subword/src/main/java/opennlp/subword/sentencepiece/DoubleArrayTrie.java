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
 * Read-only lookup over a serialized Darts-clone double-array trie, the dictionary format
 * embedded in a SentencePiece model's precompiled character map.
 *
 * <p>Each unit is one little-endian 32-bit word encoding a label, an offset to the unit's
 * children, and a leaf flag; traversal XORs the offset with the next key byte. Only the longest
 * prefix match is needed here, so this walks the byte key once and remembers the last accepting
 * state. Out-of-range unit references, which a well-formed trie never produces, fail loudly
 * rather than reading arbitrary memory.</p>
 */
final class DoubleArrayTrie {

  private final int[] units;

  /**
   * Wraps serialized trie units.
   *
   * @param data   The bytes holding the units; must not be null.
   * @param offset The offset of the first unit byte.
   * @param length The number of bytes; must be a positive multiple of four.
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
   */
  long longestPrefixMatch(byte[] key, int from, int to) {
    // The JVM's own bounds checks guard the walk; a well-formed trie never leaves the array,
    // so the translation below is the fail-loud path for corrupt data, not a hot branch.
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
        if ((unit & 0x800000FF) != b) {
          return result;
        }
        nodePos ^= offset(unit);
        if (((unit >>> 8) & 1) == 1) {
          final int value = u[nodePos] & 0x7FFFFFFF;
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
    return (units[nodePos] & 0x800000FF) == b;
  }

  // The offset from a unit to its children, as encoded by Darts-clone.
  private static int offset(int unit) {
    return (unit >>> 10) << ((unit & (1 << 9)) >>> 6);
  }
}
