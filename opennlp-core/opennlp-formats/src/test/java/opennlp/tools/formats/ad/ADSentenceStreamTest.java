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

package opennlp.tools.formats.ad;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.formats.ad.ADSentenceStream.Sentence;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Leaf;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.TreeElement;

public class ADSentenceStreamTest {

  @Test
  void testPunctuationLeaf() {
    SentenceParser parser = new SentenceParser();

    Leaf leaf = (Leaf) parser.getElement("==,");
    Assertions.assertEquals(3, leaf.getLevel());
    Assertions.assertEquals(",", leaf.getLexeme());

    leaf = (Leaf) parser.getElement(".");
    Assertions.assertEquals(1, leaf.getLevel());
    Assertions.assertEquals(".", leaf.getLexeme());

    // a line of only equals signs matches, with the last one as lexeme
    leaf = (Leaf) parser.getElement("===");
    Assertions.assertEquals(3, leaf.getLevel());
    Assertions.assertEquals("=", leaf.getLexeme());

    // non-word characters other than equals make up the lexeme
    leaf = (Leaf) parser.getElement("=!?");
    Assertions.assertEquals(2, leaf.getLevel());
    Assertions.assertEquals("!?", leaf.getLexeme());

    // a word character excludes the punctuation parse, the line is treated
    // as a bizarre leaf instead
    TreeElement element = parser.getElement("=ab");
    Assertions.assertTrue(element.isLeaf());
    leaf = (Leaf) element;
    Assertions.assertEquals(2, leaf.getLevel());
    Assertions.assertEquals("b", leaf.getLexeme());
  }

  @Test
  void testFixPunctuation() {
    SentenceParser parser = new SentenceParser();

    Sentence sentence = parser.parse(
        "<s>\nSOURCE: src\n1001 Olá mundo » .\n</s>\n", 1, false, false);
    Assertions.assertEquals("Olá mundo ».", sentence.text());

    sentence = parser.parse(
        "<s>\nSOURCE: src\n1001 Olá » , tudo bem » .\n</s>\n", 1, false, false);
    Assertions.assertEquals("Olá », tudo bem ».", sentence.text());

    // without whitespace between » and the punctuation nothing is replaced
    sentence = parser.parse(
        "<s>\nSOURCE: src\n1001 Olá mundo ».\n</s>\n", 1, false, false);
    Assertions.assertEquals("Olá mundo ».", sentence.text());
  }
}
