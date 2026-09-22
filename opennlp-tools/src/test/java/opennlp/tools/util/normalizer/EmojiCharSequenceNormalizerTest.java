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

@SuppressWarnings("deprecation")
public class EmojiCharSequenceNormalizerTest {
  private static final EmojiCharSequenceNormalizer NORMALIZER =
      EmojiCharSequenceNormalizer.getInstance();

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
  void normalizeRemovesCompleteUnicode18Emoji(String emoji, String description) {
    Assertions.assertEquals("a b", NORMALIZER.normalize("a" + emoji + "b"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0x1FAEB, 0x1FAF9, 0x1FAFA, 0x1FACC, 0x1FADD,
      0x1F6D9, 0x1FA8B, 0x1FA8C, 0x1FA8D})
  void normalizeRemovesUnicode18Additions(int codePoint) {
    Assertions.assertEquals("a b", NORMALIZER.normalize("a" + cp(codePoint) + "b"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0x1FAF9, 0x1FAFA})
  void normalizeRemovesUnicode18ThumbModifiersWithoutAcceptingDuplicateModifiers(int thumb) {
    for (int modifier = 0x1F3FB; modifier <= 0x1F3FF; modifier++) {
      String sequence = cp(thumb, modifier);
      Assertions.assertEquals("a b", NORMALIZER.normalize("a" + sequence + "b"),
          "modifier U+" + Integer.toHexString(modifier));
      String malformed = "a" + sequence + cp(modifier) + "b";
      Assertions.assertEquals(malformed, NORMALIZER.normalize(malformed));
    }
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

  @Test
  void normalizeRemovesEveryFullyQualifiedSequenceInBundledInventory() throws Exception {
    int count = 0;
    InputStream input = getClass().getResourceAsStream(
        "/opennlp/tools/util/normalizer/emoji/EmojiSequences-18.0.txt");
    Assertions.assertNotNull(input);
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(input, StandardCharsets.US_ASCII))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.startsWith("S;")) {
          continue;
        }
        StringBuilder emoji = new StringBuilder();
        int tokenStart = 2;
        for (int i = 2; i <= line.length(); i++) {
          if (i == line.length() || line.charAt(i) == ' ') {
            emoji.appendCodePoint(Integer.parseInt(line, tokenStart, i, 16));
            tokenStart = i + 1;
          }
        }
        Assertions.assertEquals(" ", NORMALIZER.normalize(emoji), line);
        count++;
      }
    }
    Assertions.assertEquals(3963, count);
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
