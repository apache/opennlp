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
 * Tests for the {@link WhitespaceTokenizer} class.
 */
public class WhitespaceTokenizerTest {

  @Test
  public void testOneToken() {
    Assert.assertEquals("one", WhitespaceTokenizer.INSTANCE.tokenize("one")[0]);
    Assert.assertEquals("one", WhitespaceTokenizer.INSTANCE.tokenize(" one")[0]);
    Assert.assertEquals("one", WhitespaceTokenizer.INSTANCE.tokenize("one ")[0]);
  }

  /**
   * Tests if it can tokenize whitespace separated tokens.
   */
  @Test
  public void testWhitespaceTokenization() {

    String text = "a b c  d     e                f    ";

    String[] tokenizedText = WhitespaceTokenizer.INSTANCE.tokenize(text);

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
    Assert.assertEquals(0, WhitespaceTokenizer.INSTANCE.tokenize("").length); // empty
    Assert.assertEquals(0, WhitespaceTokenizer.INSTANCE.tokenize(" ").length); // space
    Assert.assertEquals(0, WhitespaceTokenizer.INSTANCE.tokenize(" ").length); // tab
    Assert.assertEquals(0, WhitespaceTokenizer.INSTANCE.tokenize("     ").length);
  }

  @Test
  public void testTokenizationOfStringWithUnixNewLineTokens() {
    WhitespaceTokenizer tokenizer = WhitespaceTokenizer.INSTANCE;
    tokenizer.setKeepNewLines(true);
    
    Assert.assertEquals(2, tokenizer.tokenize("a\n").length);
    Assert.assertArrayEquals(new String[] {"a", "\n"}, tokenizer.tokenize("a\n"));
    
    Assert.assertEquals(3, tokenizer.tokenize("a\nb").length);
    Assert.assertArrayEquals(new String[] {"a", "\n", "b"}, tokenizer.tokenize("a\nb"));
    
    Assert.assertEquals(4, tokenizer.tokenize("a\n\n b").length);
    Assert.assertArrayEquals(new String[] {"a", "\n", "\n", "b"}, tokenizer.tokenize("a\n\n b"));
    
    Assert.assertEquals(7, tokenizer.tokenize("a\n\n b\n\n c").length);
    Assert.assertArrayEquals(new String[] {"a", "\n", "\n", "b", "\n", "\n", "c"},
                             tokenizer.tokenize("a\n\n b\n\n c"));
  }

  @Test
  public void testTokenizationOfStringWithWindowsNewLineTokens() {
    WhitespaceTokenizer tokenizer = WhitespaceTokenizer.INSTANCE;
    tokenizer.setKeepNewLines(true);
    
    Assert.assertEquals(3, tokenizer.tokenize("a\r\n").length);
    Assert.assertArrayEquals(new String[] {"a", "\r", "\n"}, tokenizer.tokenize("a\r\n"));
    
    Assert.assertEquals(4, tokenizer.tokenize("a\r\nb").length);
    Assert.assertArrayEquals(new String[] {"a", "\r", "\n", "b"}, tokenizer.tokenize("a\r\nb"));
    
    Assert.assertEquals(6, tokenizer.tokenize("a\r\n\r\n b").length);
    Assert.assertArrayEquals(new String[] {"a", "\r", "\n", "\r", "\n", "b"}, tokenizer
        .tokenize("a\r\n\r\n b"));
    
    Assert.assertEquals(11, tokenizer.tokenize("a\r\n\r\n b\r\n\r\n c").length);
    Assert.assertArrayEquals(new String[] {"a", "\r", "\n", "\r", "\n", "b", "\r", "\n", "\r", "\n", "c"},
                             tokenizer.tokenize("a\r\n\r\n b\r\n\r\n c"));
  }
}
