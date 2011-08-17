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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import opennlp.tools.util.Span;

import org.junit.Test;

public class TokenSampleTest {

  @Test
  public void testRetrievingContent() {
    
    String sentence = "A test";
    
    TokenSample sample = new TokenSample(sentence, new Span[]{new Span(0, 1),
        new Span(2, 6)});
   
    assertEquals("A test", sample.getText());
    
    assertEquals(new Span(0, 1), sample.getTokenSpans()[0]);
    assertEquals(new Span(2, 6), sample.getTokenSpans()[1]);
  }
  
  @Test
  public void testCreationWithDetokenizer() throws IOException {
    
    Detokenizer detokenizer = DictionaryDetokenizerTest.createLatinDetokenizer();
    
    String tokens[] = new String[]{
        "start",
        "(", // move right
        ")", // move left
        "end",
        "." // move left
    };
    
    TokenSample a = new TokenSample(detokenizer, tokens);
    
    assertEquals("start () end.", a.getText());
    
    assertEquals("start (" + TokenSample.DEFAULT_SEPARATOR_CHARS + ") end" + TokenSample.DEFAULT_SEPARATOR_CHARS + ".", a.toString());
    
    assertEquals(5, a.getTokenSpans().length);
    
    assertEquals(new Span(0, 5), a.getTokenSpans()[0]);
    assertEquals(new Span(6, 7), a.getTokenSpans()[1]);
    assertEquals(new Span(7, 8), a.getTokenSpans()[2]);
    assertEquals(new Span(9, 12), a.getTokenSpans()[3]);
    assertEquals(new Span(12, 13), a.getTokenSpans()[4]);
  }
  
  @Test
  public void testEquals() {
    assertFalse(createGoldSample() == createGoldSample());
    assertTrue(createGoldSample().equals(createGoldSample()));
    assertFalse(createPredSample().equals(createGoldSample()));
    assertFalse(createPredSample().equals(new Object()));
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
