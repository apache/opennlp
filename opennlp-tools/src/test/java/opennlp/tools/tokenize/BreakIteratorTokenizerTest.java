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

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link BreakIteratorTokenizer} class.
 */
public class BreakIteratorTokenizerTest {

  private BreakIteratorTokenizer tokenizer = new BreakIteratorTokenizer();
  
  @Test
  public void testTokenizerTest() {
    
    String[] tokenizedText = tokenizer.tokenize("The quick brown fox jumps over the lazy dog.");
    
    String[] tokens = new String[]
        {"The", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog"};
    
    Assert.assertArrayEquals(tokenizedText, tokens);
    
  }
  
  @Test
  public void testOneToken() {
    Assert.assertEquals("one", tokenizer.tokenize("one")[0]);
    Assert.assertEquals("one", tokenizer.tokenize(" one")[0]);
    Assert.assertEquals("one", tokenizer.tokenize("one ")[0]);
  }

  /**
   * Tests if it can tokenize whitespace separated tokens.
   */
  @Test
  public void testBreakIteratorTokenization() {

    String text = "a b c  d     e                f    ";

    String[] tokenizedText = tokenizer.tokenize(text);

    Assert.assertTrue("a".equals(tokenizedText[0]));
    Assert.assertTrue("b".equals(tokenizedText[1]));
    Assert.assertTrue("c".equals(tokenizedText[2]));
    Assert.assertTrue("d".equals(tokenizedText[3]));
    Assert.assertTrue("e".equals(tokenizedText[4]));
    Assert.assertTrue("f".equals(tokenizedText[5]));

    Assert.assertTrue(tokenizedText.length == 6);
  }

  @Test
  public void testTokenizationOfStringWithoutTokens() {
    Assert.assertEquals(0, tokenizer.tokenize("").length); // empty
    Assert.assertEquals(0, tokenizer.tokenize(" ").length); // space
    Assert.assertEquals(0, tokenizer.tokenize(" ").length); // tab
    Assert.assertEquals(0, tokenizer.tokenize("     ").length);
  }
}
