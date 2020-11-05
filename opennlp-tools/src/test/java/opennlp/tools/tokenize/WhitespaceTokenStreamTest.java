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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Span;

public class WhitespaceTokenStreamTest {

  /**
   * Tests for the {@link WhitespaceTokenStream} class.
   */
  @Test
  public void testWhitespace() throws IOException {
    String text = " a b c  d    e        f     ";
    ObjectStream<TokenSample> sampleStream = new TokenSampleStream(
            ObjectStreamUtils.createObjectStream(text));
    WhitespaceTokenStream tokenStream = new WhitespaceTokenStream(sampleStream);
    String read = tokenStream.read();
    Assert.assertEquals("a b c d e f", read);
  }

  @Test
  public void testSeparatedString() throws IOException {
    String text = " a b<SPLIT>c   d<SPLIT>e   ";
    ObjectStream<TokenSample> sampleStream = new TokenSampleStream(
            ObjectStreamUtils.createObjectStream(text));
    WhitespaceTokenStream tokenStream = new WhitespaceTokenStream(sampleStream);
    String read = tokenStream.read();
    Assert.assertEquals("a b c d e", read);
  }

  /**
   * Tests for the {@link TokenizerStream} correctly tokenizes whitespace separated tokens.
   */
  @Test
  public void testTokenizerStream() throws IOException {
    String text = " a b c  d    e      ";
    WhitespaceTokenizer instance = WhitespaceTokenizer.INSTANCE;
    TokenizerStream stream = new TokenizerStream(instance, ObjectStreamUtils.createObjectStream(text));
    TokenSample read = stream.read();
    Span[] tokenSpans = read.getTokenSpans();

    Assert.assertEquals(5, tokenSpans.length);

    Assert.assertEquals("a", tokenSpans[0].getCoveredText(read.getText()));
    Assert.assertEquals(new Span(1,2), tokenSpans[0]);

    Assert.assertEquals("b", tokenSpans[1].getCoveredText(read.getText()));
    Assert.assertEquals(new Span(3,4), tokenSpans[1]);

    Assert.assertEquals("c", tokenSpans[2].getCoveredText(read.getText()));
    Assert.assertEquals(new Span(5,6), tokenSpans[2]);

    Assert.assertEquals("d", tokenSpans[3].getCoveredText(read.getText()));
    Assert.assertEquals(new Span(8,9), tokenSpans[3]);

    Assert.assertEquals("e", tokenSpans[4].getCoveredText(read.getText()));
    Assert.assertEquals(new Span(13,14), tokenSpans[4]);
  }
}
