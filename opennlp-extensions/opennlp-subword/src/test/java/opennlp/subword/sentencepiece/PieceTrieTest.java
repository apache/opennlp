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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The trie's transition function held against a naive map-backed reference over randomized
 * vocabularies, so the hybrid direct-table and linear-scan node layouts are proven to enumerate
 * identical transitions; the encoder's correctness rests on that equivalence.
 */
class PieceTrieTest {

  @Test
  void testStepsMatchAMapBackedReferenceOverRandomVocabularies() {
    final Random random = new Random(7);
    for (int round = 0; round < 20; round++) {
      // Piece count crosses the direct-table threshold in both directions, so wide and narrow
      // roots are both exercised.
      final int pieceCount = 2 + random.nextInt(60);
      final Set<String> keys = new HashSet<>();
      while (keys.size() < pieceCount) {
        final int length = 1 + random.nextInt(5);
        final StringBuilder key = new StringBuilder();
        for (int i = 0; i < length; i++) {
          key.append((char) ('a' + random.nextInt(random.nextBoolean() ? 26 : 4)));
        }
        keys.add(key.toString());
      }
      final byte[][] pieces = new byte[keys.size()][];
      final int[] ids = new int[keys.size()];
      final Map<String, Integer> reference = new HashMap<>();
      int index = 0;
      for (final String key : keys) {
        pieces[index] = key.getBytes(StandardCharsets.UTF_8);
        ids[index] = index;
        reference.put(key, index);
        index++;
      }
      final PieceTrie trie = PieceTrie.build(pieces, ids);

      // Every walk over random query strings must accept exactly the reference's prefixes.
      for (int query = 0; query < 200; query++) {
        final int length = 1 + random.nextInt(8);
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < length; i++) {
          text.append((char) ('a' + random.nextInt(6)));
        }
        int node = trie.root();
        for (int i = 0; i < length; i++) {
          node = trie.step(node, (byte) text.charAt(i));
          final String prefix = text.substring(0, i + 1);
          final boolean anyKeyHasPrefix =
              keys.stream().anyMatch(k -> k.startsWith(prefix));
          if (node == PieceTrie.DEAD) {
            assertFalse(anyKeyHasPrefix, "dead end despite live prefix: " + prefix);
            break;
          }
          final Integer expected = reference.get(prefix);
          assertEquals(expected == null ? -1 : expected, trie.value(node),
              "value mismatch at prefix: " + prefix);
        }
      }
    }
  }

  @Test
  void testWideRootDispatchesAllByteValues() {
    // 200 distinct single-byte pieces force the direct-table layout at the root.
    final byte[][] pieces = new byte[200][];
    final int[] ids = new int[200];
    for (int i = 0; i < 200; i++) {
      pieces[i] = new byte[] {(byte) (i + 20)};
      ids[i] = i;
    }
    final PieceTrie trie = PieceTrie.build(pieces, ids);
    for (int b = 0; b < 256; b++) {
      final int node = trie.step(trie.root(), (byte) b);
      if (b >= 20 && b < 220) {
        assertEquals(b - 20, trie.value(node));
      } else {
        assertEquals(PieceTrie.DEAD, node);
      }
    }
  }

  @Test
  void testDuplicatePiecesFailLoudly() {
    final byte[][] pieces = {{'a'}, {'a'}};
    assertThrows(IllegalArgumentException.class, () -> PieceTrie.build(pieces, new int[] {0, 1}));
  }
}
