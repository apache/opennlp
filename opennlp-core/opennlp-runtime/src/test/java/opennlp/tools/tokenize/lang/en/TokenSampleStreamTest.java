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
  void testLeadingWhitespaceYieldsEmptyFirstToken() throws IOException {
    // a leading run gives one empty token, as String.split("\\s+") does
    TokenSample sample = stream("  a\n").next();
    Assertions.assertEquals("a", sample.getText());
    Assertions.assertArrayEquals(new Span[] {new Span(0, 0), new Span(0, 1)},
        sample.getTokenSpans());
  }

  @Test
  void testWhitespaceOnlyLineHasNoTokens() throws IOException {
    TokenSample sample = stream("   \n").next();
    Assertions.assertEquals("", sample.getText());
    Assertions.assertEquals(0, sample.getTokenSpans().length);
  }

  @Test
  void testNonAsciiWhitespaceIsNotASeparator() throws IOException {
    // no-break space is not in the ASCII whitespace set
    TokenSample sample = stream("a b c\n").next();
    Assertions.assertEquals(2, sample.getTokenSpans().length);
    Assertions.assertEquals("a b", sample.getText().substring(0, 3));
  }

  @Test
  void testQuoteAndPunctuationAttachment() throws IOException {
    // a token without an ASCII letter or digit attaches to the previous token
    TokenSample sample = stream("Hello , world !\n").next();
    Assertions.assertEquals("Hello, world!", sample.getText());
    Assertions.assertArrayEquals(new Span[] {new Span(0, 5), new Span(5, 6), new Span(7, 12),
        new Span(12, 13)}, sample.getTokenSpans());

    // a token holding a digit is a word and gets a space in front
    sample = stream("Room 101 .\n").next();
    Assertions.assertEquals("Room 101.", sample.getText());
  }
}
