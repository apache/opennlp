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

package opennlp.tools.tokenize;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Span;

public class WhitespaceTokenStreamTest {

  /**
   * Tests for the {@link WhitespaceTokenStream} class.
   */
  @Test
  void testWhitespace() throws IOException {
    String text = " a b c  d    e        f     ";
    ObjectStream<TokenSample> sampleStream = new TokenSampleStream(
        ObjectStreamUtils.createObjectStream(text));
    WhitespaceTokenStream tokenStream = new WhitespaceTokenStream(sampleStream);
    String read = tokenStream.read();
    Assertions.assertEquals("a b c d e f", read);
  }

  @Test
  void testSeparatedString() throws IOException {
    String text = " a b<SPLIT>c   d<SPLIT>e   ";
    ObjectStream<TokenSample> sampleStream = new TokenSampleStream(
        ObjectStreamUtils.createObjectStream(text));
    WhitespaceTokenStream tokenStream = new WhitespaceTokenStream(sampleStream);
    String read = tokenStream.read();
    Assertions.assertEquals("a b c d e", read);
  }

  /**
   * Tests for the {@link TokenizerStream} correctly tokenizes whitespace separated tokens.
   */
  @Test
  void testTokenizerStream() throws IOException {
    String text = " a b c  d    e      ";
    WhitespaceTokenizer instance = WhitespaceTokenizer.INSTANCE;
    TokenizerStream stream = new TokenizerStream(instance, ObjectStreamUtils.createObjectStream(text));
    TokenSample read = stream.read();
    Span[] tokenSpans = read.getTokenSpans();

    Assertions.assertEquals(5, tokenSpans.length);

    Assertions.assertEquals("a", tokenSpans[0].getCoveredText(read.getText()));
    Assertions.assertEquals(new Span(1, 2), tokenSpans[0]);

    Assertions.assertEquals("b", tokenSpans[1].getCoveredText(read.getText()));
    Assertions.assertEquals(new Span(3, 4), tokenSpans[1]);

    Assertions.assertEquals("c", tokenSpans[2].getCoveredText(read.getText()));
    Assertions.assertEquals(new Span(5, 6), tokenSpans[2]);

    Assertions.assertEquals("d", tokenSpans[3].getCoveredText(read.getText()));
    Assertions.assertEquals(new Span(8, 9), tokenSpans[3]);

    Assertions.assertEquals("e", tokenSpans[4].getCoveredText(read.getText()));
    Assertions.assertEquals(new Span(13, 14), tokenSpans[4]);
  }
}
