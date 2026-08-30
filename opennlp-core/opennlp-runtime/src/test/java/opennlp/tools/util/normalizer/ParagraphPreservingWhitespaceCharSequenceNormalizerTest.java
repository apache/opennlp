/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ParagraphPreservingWhitespaceCharSequenceNormalizerTest {

  private static ParagraphPreservingWhitespaceCharSequenceNormalizer norm() {
    return ParagraphPreservingWhitespaceCharSequenceNormalizer.getInstance();
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  static Stream<Arguments> unicodeLineBreakCodePoints() {
    return UnicodeWhitespace.lineBreaks().stream()
        .map(ws -> Arguments.of(ws.codePoint(), ws.abbreviation()));
  }

  @Test
  void getInstanceReturnsTheSharedSingleton() {
    assertSame(norm(), norm());
  }

  @Test
  void deserializationReturnsTheSharedSingleton() throws Exception {
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(norm());
    }
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
      assertSame(norm(), ois.readObject());
    }
  }

  @Test
  void horizontalRunsCollapseToASingleSpace() {
    assertEquals("a b c", norm().normalize("a \t  b" + cp(0x00A0) + cp(0x3000) + "c").toString());
  }

  @Test
  void singleLineBreakInRunCollapsesToSpace() {
    assertEquals("a b", norm().normalize("a \n b").toString());
    assertEquals("on the bank", norm().normalize("on the\nbank").toString());
  }

  @ParameterizedTest(name = "single {1} break unwraps to a space")
  @MethodSource("unicodeLineBreakCodePoints")
  void singleUnicodeLineBreakInRunCollapsesToSpace(int codePoint, String abbreviation) {
    if (codePoint == 0x000D) {
      assertEquals("a b", norm().normalize("a\rb").toString());
      assertEquals("a b", norm().normalize("a\r\nb").toString());
    } else {
      assertEquals("a b", norm().normalize("a" + cp(codePoint) + "b").toString());
    }
  }

  @Test
  void twoOrMoreLineBreaksInRunCollapseToOneNewline() {
    assertEquals("a\nb", norm().normalize("a\n\nb").toString());
    assertEquals("a\nb", norm().normalize("a\n\n\n\nb").toString());
    assertEquals("Hello world\nfoo bar", norm().normalize("Hello   world\n\n\tfoo  bar").toString());
  }

  @ParameterizedTest(name = "doubled {1} break keeps a paragraph boundary")
  @MethodSource("unicodeLineBreakCodePoints")
  void twoUnicodeLineBreaksInRunCollapseToNewline(int codePoint, String abbreviation) {
    if (codePoint == 0x000D) {
      assertEquals("a\nb", norm().normalize("a\r\n\r\nb").toString());
    } else {
      assertEquals("a\nb", norm().normalize("a" + cp(codePoint) + cp(codePoint) + "b").toString());
    }
  }

  @Test
  void carriageReturnLineFeedCountsAsOneBreak() {
    assertEquals("a b", norm().normalize("a\r\nb").toString());
    assertEquals("a\nb", norm().normalize("a\r\n\r\nb").toString());
  }

  @Test
  void gutenbergHardWrapUnwrapsButKeepsParagraphBreak() {
    final String input = """
        Alice was beginning to get very tired of sitting by her sister on the
        bank, and of having nothing to do: once or twice she had peeped into
        the book her sister was reading, but it had no pictures or
        conversations in it, "and what is the use of a book," thought Alice
        "without pictures or conversations?"

        So she was considering in her own mind (as well as she could, for the\
        """;
    final String normalized = norm().normalize(input).toString();
    assertEquals(
        "Alice was beginning to get very tired of sitting by her sister on the bank, "
            + "and of having nothing to do: once or twice she had peeped into "
            + "the book her sister was reading, but it had no pictures or "
            + "conversations in it, \"and what is the use of a book,\" thought Alice "
            + "\"without pictures or conversations?\"\n"
            + "So she was considering in her own mind (as well as she could, for the",
        normalized);
  }

  @Test
  void edgesAreTrimmed() {
    assertEquals("a", norm().normalize("  a  ").toString());
    assertEquals("a", norm().normalize("\n a \n").toString());
  }

  @Test
  void whitespaceOnlyInputNormalizesToEmpty() {
    assertEquals("", norm().normalize(" \n ").toString());
    assertEquals("", norm().normalize("").toString());
  }

  @Test
  void alignedNormalizedMatchesNormalize() {
    final String in = "  one \t two\r\n\r\nthree " + cp(0x2028) + " four  ";
    assertEquals("one two\nthree four", norm().normalize(in).toString());
    assertEquals(norm().normalize(in).toString(), norm().normalizeAligned(in).normalizedString());
  }

  @Test
  void alignmentMapsThroughCollapseAndTrim() {
    final AlignedText at = norm().normalizeAligned("  x\n\ny  ");
    assertEquals("x\ny", at.normalizedString());
    assertEquals(new Span(2, 3), at.toOriginalSpan(0, 1)); // "x"
    assertEquals(new Span(5, 6), at.toOriginalSpan(2, 3)); // "y"
  }

  @Test
  void nullTextIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> norm().normalize(null));
    assertThrows(IllegalArgumentException.class, () -> norm().normalizeAligned(null));
  }
}
