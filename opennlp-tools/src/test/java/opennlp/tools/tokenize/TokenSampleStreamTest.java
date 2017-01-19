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

/**
 * Tests for the {@link TokenSampleStream} class.
 */
public class TokenSampleStreamTest {

  /**
   * Tests if the {@link TokenSample} correctly tokenizes tokens which
   * are separated by a whitespace.
   *
   */
  @Test
  public void testParsingWhitespaceSeparatedTokens() throws IOException {
    String sampleTokens = "Slave to the wage";

    ObjectStream<TokenSample> sampleTokenStream = new TokenSampleStream(
        ObjectStreamUtils.createObjectStream(sampleTokens));

    TokenSample tokenSample = sampleTokenStream.read();

    Span tokenSpans[] = tokenSample.getTokenSpans();

    Assert.assertEquals(4, tokenSpans.length);

    Assert.assertEquals("Slave", tokenSpans[0].getCoveredText(sampleTokens));
    Assert.assertEquals("to", tokenSpans[1].getCoveredText(sampleTokens));
    Assert.assertEquals("the", tokenSpans[2].getCoveredText(sampleTokens));
    Assert.assertEquals("wage", tokenSpans[3].getCoveredText(sampleTokens));
  }

  /**
   * Tests if the {@link TokenSample} correctly tokenizes tokens which
   * are separated by the split chars.
   *
   */
  @Test
  public void testParsingSeparatedString() throws IOException {
    String sampleTokens = "a<SPLIT>b<SPLIT>c<SPLIT>d";

    ObjectStream<TokenSample> sampleTokenStream = new TokenSampleStream(
        ObjectStreamUtils.createObjectStream(sampleTokens));

    TokenSample tokenSample = sampleTokenStream.read();

    Span tokenSpans[] = tokenSample.getTokenSpans();

    Assert.assertEquals(4, tokenSpans.length);

    Assert.assertEquals("a", tokenSpans[0].getCoveredText(tokenSample.getText()));
    Assert.assertEquals(new Span(0,1), tokenSpans[0]);

    Assert.assertEquals("b", tokenSpans[1].getCoveredText(tokenSample.getText()));
    Assert.assertEquals(new Span(1,2), tokenSpans[1]);

    Assert.assertEquals("c", tokenSpans[2].getCoveredText(tokenSample.getText()));
    Assert.assertEquals(new Span(2,3), tokenSpans[2]);

    Assert.assertEquals("d", tokenSpans[3].getCoveredText(tokenSample.getText()));
    Assert.assertEquals(new Span(3,4), tokenSpans[3]);

  }

  /**
   * Tests if the {@link TokenSample} correctly tokenizes tokens which
   * are separated by whitespace and by the split chars.
   *
   */
  @Test
  public void testParsingWhitespaceAndSeparatedString() throws IOException {
    String sampleTokens = "a b<SPLIT>c d<SPLIT>e";

    ObjectStream<TokenSample> sampleTokenStream = new TokenSampleStream(
        ObjectStreamUtils.createObjectStream(sampleTokens));

    TokenSample tokenSample = sampleTokenStream.read();

    Span tokenSpans[] = tokenSample.getTokenSpans();

    Assert.assertEquals(5, tokenSpans.length);

    Assert.assertEquals("a", tokenSpans[0].getCoveredText(tokenSample.getText()));
    Assert.assertEquals("b", tokenSpans[1].getCoveredText(tokenSample.getText()));
    Assert.assertEquals("c", tokenSpans[2].getCoveredText(tokenSample.getText()));
    Assert.assertEquals("d", tokenSpans[3].getCoveredText(tokenSample.getText()));
    Assert.assertEquals("e", tokenSpans[4].getCoveredText(tokenSample.getText()));
  }
}
