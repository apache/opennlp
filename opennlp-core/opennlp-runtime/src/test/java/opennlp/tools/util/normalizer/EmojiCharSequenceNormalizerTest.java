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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class EmojiCharSequenceNormalizerTest {
  private static final EmojiCharSequenceNormalizer NORMALIZER =
      EmojiCharSequenceNormalizer.getInstance();

  /**
   * The number of fully-qualified sequences in the Emoji 17.0 emoji-test.txt, the same number
   * dev/UnicodeEmojiSequenceGenerator.java pins for that release.
   */
  private static final int EMOJI_17_SEQUENCE_COUNT = 3944;

  private static String cp(int... codePoints) {
    return new String(codePoints, 0, codePoints.length);
  }

  private static Stream<Arguments> emojiSequences() {
    return Stream.of(
        Arguments.of(cp(0x1F600), "supplementary singleton"),
        Arguments.of("\u231A", "BMP default presentation"),
        Arguments.of("\u2764\uFE0F", "BMP explicit presentation"),
        Arguments.of(cp(0x1F44D, 0x1F3FD), "modifier sequence"),
        Arguments.of(cp(0x1F1E9, 0x1F1EA), "flag sequence"),
        Arguments.of("7\uFE0F\u20E3", "keycap sequence"),
        Arguments.of(cp(0x1F468) + "\u200D" + cp(0x1F469) + "\u200D" + cp(0x1F467),
            "ZWJ sequence"),
        Arguments.of(cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F),
            "tag sequence"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("emojiSequences")
  void normalizeRemovesCompleteEmoji(String emoji, String description) {
    Assertions.assertEquals("a b", NORMALIZER.normalize("a" + emoji + "b"));
  }

  /**
   * The nine code points and ten modifier sequences that Emoji 18.0 added on top of 17.0. The
   * bundled inventory is Emoji 17.0, the same version as the other bundled Unicode data, so
   * these are not emoji to this normalizer yet.
   */
  private static Stream<String> emoji18OnlySequences() {
    return Stream.of(cp(0x1FAEB), cp(0x1FACC), cp(0x1FADD), cp(0x1F6D9), cp(0x1FA8B),
        cp(0x1FA8C), cp(0x1FA8D), cp(0x1FAF9), cp(0x1FAFA),
        cp(0x1FAF9, 0x1F3FB), cp(0x1FAF9, 0x1F3FC), cp(0x1FAF9, 0x1F3FD),
        cp(0x1FAF9, 0x1F3FE), cp(0x1FAF9, 0x1F3FF),
        cp(0x1FAFA, 0x1F3FB), cp(0x1FAFA, 0x1F3FC), cp(0x1FAFA, 0x1F3FD),
        cp(0x1FAFA, 0x1F3FE), cp(0x1FAFA, 0x1F3FF));
  }

  @ParameterizedTest
  @MethodSource("emoji18OnlySequences")
  void normalizeKeepsSequencesAddedAfterEmoji17(String sequence) {
    Assertions.assertEquals("a" + sequence + "b", NORMALIZER.normalize("a" + sequence + "b"));
  }

  @Test
  void normalizeCollapsesAdjacentCompleteEmojiOnly() {
    String adjacent = cp(0x1F600) + "\u2764\uFE0F" + cp(0x1F1E9, 0x1F1EA);
    Assertions.assertEquals("a b", NORMALIZER.normalize("a" + adjacent + "b"));
    Assertions.assertEquals("a   b",
        NORMALIZER.normalize("a" + cp(0x1F600) + " " + cp(0x1F603) + "b"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "plain text", "a-b", "well-known", "\uD801\uDC12",
      "\uD840\uDC00", "\uDB80\uDC00", "\uE000", "\uFFFF", "\u2764", "123", "#", "*"})
  void normalizePreservesNonEmojiText(String text) {
    Assertions.assertEquals(text, NORMALIZER.normalize(text));
  }

  @Test
  void normalizeDoesNotTreatKeycapBasesAsCandidateByProximity() {
    Assertions.assertEquals("123 abc", NORMALIZER.normalize("123" + cp(0x1F600) + "abc"));
    Assertions.assertEquals(" 1", NORMALIZER.normalize(cp(0x1F600) + "1"));
    Assertions.assertEquals("x# ", NORMALIZER.normalize("x#" + cp(0x1F600)));
  }

  @Test
  void normalizeTextPresentationSymbolDoesNotPoisonAdjacentEmoji() {
    Assertions.assertEquals("\u2764 ", NORMALIZER.normalize("\u2764" + cp(0x1F600)));
    Assertions.assertEquals(" \u2764", NORMALIZER.normalize(cp(0x1F600) + "\u2764"));
    Assertions.assertEquals("a\u231A\uFE0Eb",
        NORMALIZER.normalize("a\u231A\uFE0Eb"));
  }

  @Test
  void normalizePreservesConnectedNonFullyQualifiedZwjCandidate() {
    String heartOnFire = "\u2764\u200D" + cp(0x1F525);
    Assertions.assertEquals("a" + heartOnFire + "b",
        NORMALIZER.normalize("a" + heartOnFire + "b"));

    String textSymbolConnection = "\u2764\u200D" + cp(0x1F600);
    Assertions.assertEquals("a" + textSymbolConnection + "b",
        NORMALIZER.normalize("a" + textSymbolConnection + "b"));
  }

  @Test
  void normalizePreservesLeadingOrphanComponentConnectedToEmoji() {
    String connected = "\u200D" + cp(0x1F600);
    Assertions.assertEquals("a" + connected + "b", NORMALIZER.normalize("a" + connected + "b"));
  }

  private static Stream<String> malformedConnectedCandidates() {
    return Stream.of(cp(0x1F600) + "\u200D", cp(0x1F600, 0x1F3FD),
        cp(0x1F1E9, 0x1F1EA, 0x1F1EB), "7\uFE0F", "7\u20E3",
        cp(0x1F3F4, 0xE0067, 0xE0062), cp(0x1F600) + "\u200D" + cp(0x1F600));
  }

  @ParameterizedTest
  @MethodSource("malformedConnectedCandidates")
  void normalizePreservesConnectedMalformedCandidateAtomically(String malformed) {
    Assertions.assertEquals("a" + malformed + "b", NORMALIZER.normalize("a" + malformed + "b"));
  }

  /**
   * A stray joiner, modifier, selector, flag letter or tag connects only to the complete
   * sequence right before it and to the one right after it. Complete sequences that merely
   * touch that malformed part are removed as usual.
   */
  private static Stream<Arguments> malformedTails() {
    return Stream.of(
        Arguments.of(cp(0x1F600, 0x1F600, 0x1F600) + "\u200D", " " + cp(0x1F600) + "\u200D"),
        Arguments.of(cp(0x1F600, 0x1F600, 0x1F1E9), " " + cp(0x1F600, 0x1F1E9)),
        Arguments.of(cp(0x1F1E9, 0x1F1EA, 0x1F1EB, 0x1F1F7, 0x1F1EE),
            " " + cp(0x1F1EB, 0x1F1F7, 0x1F1EE)),
        Arguments.of(cp(0x1F600) + "\u200D" + cp(0x1F600, 0x1F600),
            cp(0x1F600) + "\u200D" + cp(0x1F600) + " "),
        Arguments.of(cp(0x1F600, 0x1F600) + "\u200D" + cp(0x1F600, 0x1F600),
            " " + cp(0x1F600) + "\u200D" + cp(0x1F600) + " "),
        Arguments.of(cp(0x1F600, 0x1F600, 0x1F3FD, 0x1F600, 0x1F600),
            " " + cp(0x1F600, 0x1F3FD, 0x1F600) + " "));
  }

  @ParameterizedTest
  @MethodSource("malformedTails")
  void normalizeEndsRunAtTheLastCompleteSequenceBeforeAMalformedTail(String text,
                                                                     String expected) {
    Assertions.assertEquals("a" + expected + "b", NORMALIZER.normalize("a" + text + "b"));
  }

  /**
   * Keyboards and older text often add U+FE0F after an emoji that already has emoji
   * presentation. One such selector right after a complete sequence is removed with it; a
   * second one is a stray component like any other.
   */
  private static Stream<Arguments> redundantEmojiPresentationSelectors() {
    return Stream.of(
        Arguments.of(cp(0x1F600) + "\uFE0F", " "),
        Arguments.of(cp(0x1F44D) + "\uFE0F", " "),
        Arguments.of("\u231A\uFE0F", " "),
        Arguments.of("\u2764\uFE0F\uFE0F", " "),
        Arguments.of(cp(0x1F44D, 0x1F3FD) + "\uFE0F", " "),
        Arguments.of(cp(0x1F600) + "\uFE0F" + cp(0x1F603), " "),
        Arguments.of(cp(0x1F600) + "\uFE0F\uFE0F", cp(0x1F600) + "\uFE0F\uFE0F"),
        Arguments.of(cp(0x1F600) + "\uFE0F\u200D", cp(0x1F600) + "\uFE0F\u200D"));
  }

  @ParameterizedTest
  @MethodSource("redundantEmojiPresentationSelectors")
  void normalizeRemovesOneRedundantEmojiPresentationSelectorWithTheEmoji(String text,
                                                                         String expected) {
    Assertions.assertEquals("a" + expected + "b", NORMALIZER.normalize("a" + text + "b"));
  }

  @Test
  void normalizePreservesOrphanComponents() {
    String text = "a\u200D\uFE0F\u20E3" + cp(0x1F3FD, 0xE0067, 0xE007F) + "b";
    Assertions.assertEquals(text, NORMALIZER.normalize(text));
  }

  @Test
  void normalizePreservesMalformedUtf16() {
    String text = "a\uD83Cb\uDC00c\uD83C" + cp(0x1F600) + "\uDC00d";
    Assertions.assertEquals("a\uD83Cb\uDC00c\uD83C \uDC00d", NORMALIZER.normalize(text));
  }

  @Test
  void normalizeRejectsNull() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> NORMALIZER.normalize(null));
  }

  @Test
  void normalizeAcceptsAnyCharSequenceAndIsIdempotent() {
    String text = "a" + cp(0x1F600) + "b";
    Assertions.assertEquals("a b", NORMALIZER.normalize(new StringBuilder(text)));
    Assertions.assertEquals("a b", NORMALIZER.normalize(CharBuffer.wrap(text.toCharArray())));
    CharSequence once = NORMALIZER.normalize(text);
    Assertions.assertEquals(once.toString(), NORMALIZER.normalize(once).toString());
  }

  /**
   * Known Emoji 17.0 sequences, one or two per emoji-test.txt group, written down from the
   * published list rather than read from the bundled file, so a wrong or truncated inventory
   * fails here. The last seven are new in 17.0.
   */
  private static Stream<Arguments> knownEmoji17Sequences() {
    return Stream.of(
        Arguments.of(cp(0x1F600), "Smileys & Emotion: grinning face"),
        Arguments.of(cp(0x1F636, 0x200D, 0x1F32B, 0xFE0F), "Smileys & Emotion: face in clouds"),
        Arguments.of(cp(0x1F44D, 0x1F3FD), "People & Body: thumbs up, medium skin tone"),
        Arguments.of(cp(0x1F9D1, 0x200D, 0x1F4BB), "People & Body: technologist"),
        Arguments.of(cp(0x1FAF1, 0x1F3FB, 0x200D, 0x1FAF2, 0x1F3FF),
            "People & Body: handshake, two skin tones"),
        Arguments.of(cp(0x1F436), "Animals & Nature: dog face"),
        Arguments.of(cp(0x1F415, 0x200D, 0x1F9BA), "Animals & Nature: service dog"),
        Arguments.of(cp(0x1F34E), "Food & Drink: red apple"),
        Arguments.of(cp(0x1F697), "Travel & Places: automobile"),
        Arguments.of(cp(0x26BD), "Activities: soccer ball"),
        Arguments.of(cp(0x1F4F1), "Objects: mobile phone"),
        Arguments.of(cp(0x2764, 0xFE0F), "Symbols: red heart"),
        Arguments.of(cp(0x2764, 0xFE0F, 0x200D, 0x1F525), "Symbols: heart on fire"),
        Arguments.of(cp(0x0023, 0xFE0F, 0x20E3), "Symbols: keycap number sign"),
        Arguments.of(cp(0x1F51F), "Symbols: keycap 10"),
        Arguments.of(cp(0x1F1FA, 0x1F1F8), "Flags: United States"),
        Arguments.of(cp(0x1F3F3, 0xFE0F, 0x200D, 0x1F308), "Flags: rainbow flag"),
        Arguments.of(cp(0x1F3F4, 0xE0067, 0xE0062, 0xE0073, 0xE0063, 0xE0074, 0xE007F),
            "Flags: Scotland"),
        Arguments.of(cp(0x1FAEA), "Emoji 17.0: distorted face"),
        Arguments.of(cp(0x1FAC8), "Emoji 17.0: hairy creature"),
        Arguments.of(cp(0x1FACD), "Emoji 17.0: orca"),
        Arguments.of(cp(0x1FAEF), "Emoji 17.0: fight cloud"),
        Arguments.of(cp(0x1F6D8), "Emoji 17.0: landslide"),
        Arguments.of(cp(0x1FA8A), "Emoji 17.0: trombone"),
        Arguments.of(cp(0x1FA8E), "Emoji 17.0: treasure chest"));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("knownEmoji17Sequences")
  void normalizeRemovesKnownEmoji17Sequences(String emoji, String description) {
    Assertions.assertEquals("a b", NORMALIZER.normalize("a" + emoji + "b"));
  }

  @Test
  void bundledInventoryHoldsTheEmoji17SequenceCount() {
    Assertions.assertEquals(EMOJI_17_SEQUENCE_COUNT,
        UnicodeEmojiSequences.getInstance().sequenceCount());
  }

  @Test
  void normalizeRemovesEveryFullyQualifiedSequenceInBundledInventory() throws Exception {
    int count = 0;
    InputStream input = getClass().getResourceAsStream(
        "/opennlp/tools/util/normalizer/EmojiSequences.txt");
    Assertions.assertNotNull(input);
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(input, StandardCharsets.US_ASCII))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.startsWith("S;")) {
          continue;
        }
        String emoji = HexCodePoints.decodeSequence(line.substring(2));
        Assertions.assertEquals(" ", NORMALIZER.normalize(emoji), line);
        count++;
      }
    }
    Assertions.assertEquals(EMOJI_17_SEQUENCE_COUNT, count);
  }

  @Test
  void serializationKeepsSingleton() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(NORMALIZER);
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      Assertions.assertSame(NORMALIZER, in.readObject());
    }
  }
}
