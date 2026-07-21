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

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static opennlp.tools.util.normalizer.NormalizerTestUtil.cp;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmojiFlagsTest {

  // Regional indicator symbols A..Z.
  private static int ri(char letter) {
    return 0x1F1E6 + (letter - 'A');
  }

  // Tag characters spelling an ASCII string, without the terminator.
  private static String tags(String ascii) {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < ascii.length(); i++) {
      sb.appendCodePoint(0xE0000 + ascii.charAt(i));
    }
    return sb.toString();
  }

  private static final String BLACK_FLAG = cp(0x1F3F4);
  private static final String CANCEL = cp(0xE007F);

  @Test
  void regionalIndicatorPairsDecodeToIso3166Alpha2() {
    assertEquals("DE", EmojiFlags.isoRegion(cp(ri('D'), ri('E'))).orElseThrow());
    assertEquals("US", EmojiFlags.isoRegion(cp(ri('U'), ri('S'))).orElseThrow());
    assertEquals("JP", EmojiFlags.isoRegion(cp(ri('J'), ri('P'))).orElseThrow());
    // Exceptionally reserved and macro-region codes decode the same way (UN, EU).
    assertEquals("UN", EmojiFlags.isoRegion(cp(ri('U'), ri('N'))).orElseThrow());
    assertEquals("EU", EmojiFlags.isoRegion(cp(ri('E'), ri('U'))).orElseThrow());
  }

  @Test
  void decodingDoesNotCheckAssignment() {
    // Whether XX is an assigned region is a join-time question against the user's gazetteer, not
    // a bundled or derived fact; the pair decodes mechanically.
    assertEquals("XX", EmojiFlags.isoRegion(cp(ri('X'), ri('X'))).orElseThrow());
  }

  @Test
  void subdivisionTagSequencesDecodeToIso3166Dash2() {
    assertEquals("GB-ENG",
        EmojiFlags.isoRegion(BLACK_FLAG + tags("gbeng") + CANCEL).orElseThrow());
    assertEquals("GB-SCT",
        EmojiFlags.isoRegion(BLACK_FLAG + tags("gbsct") + CANCEL).orElseThrow());
    assertEquals("GB-WLS",
        EmojiFlags.isoRegion(BLACK_FLAG + tags("gbwls") + CANCEL).orElseThrow());
    // Digits are valid in a subdivision suffix (ISO 3166-2 allows alphanumeric suffixes).
    assertEquals("FR-75", EmojiFlags.isoRegion(BLACK_FLAG + tags("fr75") + CANCEL).orElseThrow());
  }

  @Test
  void nonFlagsAreEmptyNotErrors() {
    assertEquals(Optional.empty(), EmojiFlags.isoRegion(""));
    assertEquals(Optional.empty(), EmojiFlags.isoRegion("word"));
    assertEquals(Optional.empty(), EmojiFlags.isoRegion(cp(0x1F642)));
    // A lone waving black flag is an ordinary pictograph, not a region flag.
    assertEquals(Optional.empty(), EmojiFlags.isoRegion(BLACK_FLAG));
    // The ZWJ pirate flag is a different mechanism (U+1F3F4 U+200D U+2620 U+FE0F), not a tag
    // sequence; it is not flag-shaped for region decoding.
    assertEquals(Optional.empty(),
        EmojiFlags.isoRegion(cp(0x1F3F4, 0x200D, 0x2620, 0xFE0F)));
    // Leading ordinary letter: not flag-shaped even if regional indicators follow.
    assertEquals(Optional.empty(), EmojiFlags.isoRegion("A" + cp(ri('D'), ri('E'))));
  }

  static Stream<String> malformedRegionalIndicatorSequences() {
    // A lone indicator, an odd run, adjacent flags passed as one symbol, and an indicator followed
    // by other content are all malformed for the strict decoder.
    return Stream.of(
        cp(ri('D')),
        cp(ri('D'), ri('E'), ri('F')),
        cp(ri('D'), ri('E'), ri('F'), ri('R')),
        cp(ri('D')) + "E",
        cp(ri('D'), ri('E')) + "!");
  }

  @ParameterizedTest
  @MethodSource("malformedRegionalIndicatorSequences")
  void malformedRegionalIndicatorSequencesFailLoud(String input) {
    assertThrows(IllegalArgumentException.class, () -> EmojiFlags.isoRegion(input));
  }

  static Stream<String> malformedTagSequences() {
    return Stream.of(
        BLACK_FLAG + tags("gbeng"),
        BLACK_FLAG + CANCEL,
        BLACK_FLAG + tags("gb") + CANCEL,
        BLACK_FLAG + tags("1beng") + CANCEL,
        BLACK_FLAG + tags("gb") + "e" + tags("ng") + CANCEL,
        BLACK_FLAG + tags("gbeng") + CANCEL + "x");
  }

  @ParameterizedTest
  @MethodSource("malformedTagSequences")
  void malformedTagSequencesFailLoud(String input) {
    assertThrows(IllegalArgumentException.class, () -> EmojiFlags.isoRegion(input));
  }

  @Test
  void isFlagIsTotalOverMalformedInput() {
    // The bulk predicate never throws on degenerate text (per-token annotation must survive a
    // stray lone indicator in real-world input); it is true exactly for well-formed flags.
    assertTrue(EmojiFlags.isFlag(cp(ri('D'), ri('E'))));
    assertTrue(EmojiFlags.isFlag(BLACK_FLAG + tags("gbeng") + CANCEL));
    assertFalse(EmojiFlags.isFlag(cp(ri('D'))));
    assertFalse(EmojiFlags.isFlag(cp(ri('D'), ri('E'), ri('F'))));
    assertFalse(EmojiFlags.isFlag(BLACK_FLAG + tags("gbeng")));
    assertFalse(EmojiFlags.isFlag(BLACK_FLAG));
    assertFalse(EmojiFlags.isFlag("word"));
    assertFalse(EmojiFlags.isFlag(""));
  }

  @Test
  void nullFailsLoud() {
    assertThrows(IllegalArgumentException.class, () -> EmojiFlags.isoRegion(null));
    assertThrows(IllegalArgumentException.class, () -> EmojiFlags.isFlag(null));
  }
}
