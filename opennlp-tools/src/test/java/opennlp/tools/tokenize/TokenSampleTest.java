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

import opennlp.tools.util.Span;

public class TokenSampleTest {

  @Test
  public void testRetrievingContent() {

    String sentence = "A test";

    TokenSample sample = new TokenSample(sentence, new Span[]{new Span(0, 1),
        new Span(2, 6)});

    Assert.assertEquals("A test", sample.getText());

    Assert.assertEquals(new Span(0, 1), sample.getTokenSpans()[0]);
    Assert.assertEquals(new Span(2, 6), sample.getTokenSpans()[1]);
  }

  @Test
  public void testCreationWithDetokenizer() throws IOException {

    Detokenizer detokenizer = DictionaryDetokenizerTest.createLatinDetokenizer();

    String tokens[] = new String[]{
        "start",
        "(", // move right
        ")", // move left
        "end",
        ".", // move left
        "hyphen",
        "-", // move both
        "string",
        "."
    };

    TokenSample a = new TokenSample(detokenizer, tokens);

    Assert.assertEquals("start () end. hyphen-string.", a.getText());
    Assert.assertEquals("start (" + TokenSample.DEFAULT_SEPARATOR_CHARS + ") end"
        + TokenSample.DEFAULT_SEPARATOR_CHARS + "."
        + " hyphen" + TokenSample.DEFAULT_SEPARATOR_CHARS + "-" + TokenSample.DEFAULT_SEPARATOR_CHARS
        + "string" + TokenSample.DEFAULT_SEPARATOR_CHARS + ".", a.toString());

    Assert.assertEquals(9, a.getTokenSpans().length);

    Assert.assertEquals(new Span(0, 5), a.getTokenSpans()[0]);
    Assert.assertEquals(new Span(6, 7), a.getTokenSpans()[1]);
    Assert.assertEquals(new Span(7, 8), a.getTokenSpans()[2]);
    Assert.assertEquals(new Span(9, 12), a.getTokenSpans()[3]);
    Assert.assertEquals(new Span(12, 13), a.getTokenSpans()[4]);

    Assert.assertEquals(new Span(14, 20), a.getTokenSpans()[5]);
    Assert.assertEquals(new Span(20, 21), a.getTokenSpans()[6]);
    Assert.assertEquals(new Span(21, 27), a.getTokenSpans()[7]);
    Assert.assertEquals(new Span(27, 28), a.getTokenSpans()[8]);
  }

  @Test
  public void testEquals() {
    Assert.assertFalse(createGoldSample() == createGoldSample());
    Assert.assertTrue(createGoldSample().equals(createGoldSample()));
    Assert.assertFalse(createPredSample().equals(createGoldSample()));
    Assert.assertFalse(createPredSample().equals(new Object()));
  }

  public static TokenSample createGoldSample() {
    return new TokenSample("A test.", new Span[] { new Span(0, 1),
        new Span(2, 6) });
  }

  public static TokenSample createPredSample() {
    return new TokenSample("A test.", new Span[] { new Span(0, 3),
        new Span(2, 6) });
  }
}
