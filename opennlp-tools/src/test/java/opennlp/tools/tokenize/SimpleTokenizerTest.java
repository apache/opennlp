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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link SimpleTokenizer} class.
 */
public class SimpleTokenizerTest {

  // The SimpleTokenizer is thread safe
  private final SimpleTokenizer mTokenizer = SimpleTokenizer.INSTANCE;

  /**
   * Tests if it can tokenize whitespace separated tokens.
   */
  @Test
  void testWhitespaceTokenization() {

    String text = "a b c  d     e                f    ";

    String[] tokenizedText = mTokenizer.tokenize(text);

    Assertions.assertEquals("a", tokenizedText[0]);
    Assertions.assertEquals("b", tokenizedText[1]);
    Assertions.assertEquals("c", tokenizedText[2]);
    Assertions.assertEquals("d", tokenizedText[3]);
    Assertions.assertEquals("e", tokenizedText[4]);
    Assertions.assertEquals("f", tokenizedText[5]);

    Assertions.assertEquals(6, tokenizedText.length);
  }

  /**
   * Tests if it can tokenize a word and a dot.
   */
  @Test
  void testWordDotTokenization() {
    String text = "a.";

    String[] tokenizedText = mTokenizer.tokenize(text);

    Assertions.assertEquals("a", tokenizedText[0]);
    Assertions.assertEquals(".", tokenizedText[1]);
    Assertions.assertEquals(2, tokenizedText.length);
  }

  /**
   * Tests if it can tokenize a word and numeric.
   */
  @Test
  void testWordNumericTokeniztation() {
    String text = "305KW";

    String[] tokenizedText = mTokenizer.tokenize(text);

    Assertions.assertEquals("305", tokenizedText[0]);
    Assertions.assertEquals("KW", tokenizedText[1]);
    Assertions.assertEquals(2, tokenizedText.length);
  }

  @Test
  void testWordWithOtherTokenization() {
    String text = "rebecca.sleep()";

    String[] tokenizedText = mTokenizer.tokenize(text);

    Assertions.assertEquals("rebecca", tokenizedText[0]);
    Assertions.assertEquals(".", tokenizedText[1]);
    Assertions.assertEquals("sleep", tokenizedText[2]);
    Assertions.assertEquals("(", tokenizedText[3]);
    Assertions.assertEquals(")", tokenizedText[4]);
    Assertions.assertEquals(5, tokenizedText.length);
  }

  @Test
  void testTokenizationOfStringWithUnixNewLineTokens() {
    SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
    tokenizer.setKeepNewLines(true);

    Assertions.assertEquals(2, tokenizer.tokenize("a\n").length);
    Assertions.assertArrayEquals(new String[] {"a", "\n"}, tokenizer.tokenize("a\n"));

    Assertions.assertEquals(3, tokenizer.tokenize("a\nb").length);
    Assertions.assertArrayEquals(new String[] {"a", "\n", "b"}, tokenizer.tokenize("a\nb"));

    Assertions.assertEquals(4, tokenizer.tokenize("a\n\n b").length);
    Assertions.assertArrayEquals(new String[] {"a", "\n", "\n", "b"}, tokenizer.tokenize("a\n\n b"));

    Assertions.assertEquals(7, tokenizer.tokenize("a\n\n b\n\n c").length);
    Assertions.assertArrayEquals(new String[] {"a", "\n", "\n", "b", "\n", "\n", "c"},
        tokenizer.tokenize("a\n\n b\n\n c"));
  }

  @Test
  void testTokenizationOfStringWithWindowsNewLineTokens() {
    SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;
    tokenizer.setKeepNewLines(true);

    Assertions.assertEquals(3, tokenizer.tokenize("a\r\n").length);
    Assertions.assertArrayEquals(new String[] {"a", "\r", "\n"}, tokenizer.tokenize("a\r\n"));

    Assertions.assertEquals(4, tokenizer.tokenize("a\r\nb").length);
    Assertions.assertArrayEquals(new String[] {"a", "\r", "\n", "b"}, tokenizer.tokenize("a\r\nb"));

    Assertions.assertEquals(6, tokenizer.tokenize("a\r\n\r\n b").length);
    Assertions.assertArrayEquals(new String[] {"a", "\r", "\n", "\r", "\n", "b"}, tokenizer
        .tokenize("a\r\n\r\n b"));

    Assertions.assertEquals(11, tokenizer.tokenize("a\r\n\r\n b\r\n\r\n c").length);
    Assertions.assertArrayEquals(new String[] {"a", "\r", "\n", "\r", "\n", "b", "\r", "\n", "\r", "\n", "c"},
        tokenizer.tokenize("a\r\n\r\n b\r\n\r\n c"));
  }
}
