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

import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LineBreakPreservingWhitespaceCharSequenceNormalizerTest {

  private static LineBreakPreservingWhitespaceCharSequenceNormalizer norm() {
    return LineBreakPreservingWhitespaceCharSequenceNormalizer.getInstance();
  }

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void getInstanceReturnsTheSharedSingleton() {
    assertSame(norm(), norm());
  }

  @Test
  void horizontalRunsCollapseToASingleSpace() {
    assertEquals("a b c", norm().normalize("a \t  b" + cp(0x00A0) + cp(0x3000) + "c").toString());
  }

  @Test
  void runContainingALineBreakCollapsesToOneNewline() {
    assertEquals("a\nb", norm().normalize("a \n b").toString());
    assertEquals("one\ntwo", norm().normalize("one\n\n\ntwo").toString());
  }

  @Test
  void carriageReturnLineFeedCollapsesToOneNewline() {
    assertEquals("a\nb", norm().normalize("a\r\nb").toString());
  }

  @Test
  void unicodeLineAndParagraphSeparatorsCountAsBreaks() {
    assertEquals("a\nb", norm().normalize("a" + cp(0x2028) + "b").toString());
    assertEquals("a\nb", norm().normalize("a " + cp(0x2029) + " b").toString());
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
    assertEquals(norm().normalize(in).toString(), norm().normalizeAligned(in).normalizedString());
  }

  @Test
  void alignmentMapsThroughCollapseAndTrim() {
    // "  x\n\ny  " normalizes to "x\ny": the collapse and the edge trim are two stages composed
    // with andThen, and a span on the output must map through both back to the original.
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
