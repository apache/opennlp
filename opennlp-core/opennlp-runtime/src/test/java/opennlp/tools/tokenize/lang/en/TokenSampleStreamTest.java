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

package opennlp.tools.tokenize.lang.en;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.util.Span;

public class TokenSampleStreamTest {

  private static TokenSampleStream stream(String text) throws IOException {
    return new TokenSampleStream(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void testReadsTokensAndSpans() throws IOException {
    TokenSampleStream stream = stream("The dog 's -LRB- big -RRB- .\n");
    Assertions.assertTrue(stream.hasNext());
    TokenSample sample = stream.next();
    Assertions.assertEquals("The dog's ( big).", sample.getText());
    Assertions.assertArrayEquals(new Span[] {new Span(0, 3), new Span(4, 7), new Span(7, 9),
        new Span(10, 11), new Span(12, 15), new Span(15, 16), new Span(16, 17)},
        sample.getTokenSpans());
    Assertions.assertFalse(stream.hasNext());
  }

  @Test
  void testCollapsesWhitespaceRuns() throws IOException {
    TokenSample sample = stream("a \t b\n").next();
    Assertions.assertEquals("a b", sample.getText());
    Assertions.assertEquals(2, sample.getTokenSpans().length);
  }

  @Test
  void testLeadingAndTrailingWhitespaceAddNoTokens() throws IOException {
    // a leading run is a separator like any other, not an empty first token with an empty span
    TokenSample sample = stream("  a\n").next();
    Assertions.assertEquals("a", sample.getText());
    Assertions.assertArrayEquals(new Span[] {new Span(0, 1)}, sample.getTokenSpans());

    sample = stream("\t a b \t\n").next();
    Assertions.assertEquals("a b", sample.getText());
    Assertions.assertArrayEquals(new Span[] {new Span(0, 1), new Span(2, 3)},
        sample.getTokenSpans());
  }

  @Test
  void testWhitespaceOnlyLineHasNoTokens() throws IOException {
    TokenSample sample = stream("   \n").next();
    Assertions.assertEquals("", sample.getText());
    Assertions.assertEquals(0, sample.getTokenSpans().length);
  }

  @Test
  void testUnicodeWhitespaceSeparatesTokens() throws IOException {
    // no-break space, em space, ideographic space, and next line separate tokens as a plain
    // space does, in every whitespace mode
    TokenSample sample = stream("a\u00A0b\u2003c\u3000d\u0085e\n").next();
    Assertions.assertEquals("a b c d e", sample.getText());
    Assertions.assertArrayEquals(new Span[] {new Span(0, 1), new Span(2, 3), new Span(4, 5),
        new Span(6, 7), new Span(8, 9)}, sample.getTokenSpans());
  }

  @Test
  void testInformationSeparatorIsNotWhitespace() throws IOException {
    // U+001C is not Unicode White_Space, so it stays inside the token in every mode
    TokenSample sample = stream("a\u001Cb\n").next();
    Assertions.assertEquals("a\u001Cb", sample.getText());
    Assertions.assertArrayEquals(new Span[] {new Span(0, 3)}, sample.getTokenSpans());
  }

  @Test
  void testLineWithoutTokensResetsQuoteState() throws IOException {
    // an opening quote on the first line leaves the quote state open, so a quote on the next
    // line closes it and attaches to the following word
    TokenSampleStream stream = stream("x \" y\n\" z\n");
    Assertions.assertEquals("x \"y", stream.next().getText());
    Assertions.assertEquals("\" z", stream.next().getText());

    // a line without tokens in between resets the state, so the quote opens again
    stream = stream("x \" y\n\n\" z\n");
    Assertions.assertEquals("x \"y", stream.next().getText());
    Assertions.assertEquals("", stream.next().getText());
    Assertions.assertEquals("\"z", stream.next().getText());
  }

  @Test
  void testNonAsciiWordsAreWords() throws IOException {
    // a token made of non-ASCII letters or digits is a word and gets a space in front,
    // it does not attach to the previous token as punctuation would
    TokenSample sample = stream("caf\u00E9 \u03A9 \u65E5\u672C \u0661 .\n").next();
    Assertions.assertEquals("caf\u00E9 \u03A9 \u65E5\u672C \u0661.", sample.getText());
    Assertions.assertEquals(5, sample.getTokenSpans().length);

    // a supplementary-plane letter counts too
    sample = stream("x \uD801\uDC12 y\n").next();
    Assertions.assertEquals("x \uD801\uDC12 y", sample.getText());
  }

  @Test
  void testQuoteAndPunctuationAttachment() throws IOException {
    // a token without a letter or digit of any script attaches to the previous token
    TokenSample sample = stream("Hello , world !\n").next();
    Assertions.assertEquals("Hello, world!", sample.getText());
    Assertions.assertArrayEquals(new Span[] {new Span(0, 5), new Span(5, 6), new Span(7, 12),
        new Span(12, 13)}, sample.getTokenSpans());

    // a token holding a digit is a word and gets a space in front
    sample = stream("Room 101 .\n").next();
    Assertions.assertEquals("Room 101.", sample.getText());
  }
}
