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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.Leaf;
import opennlp.tools.formats.ad.ADSentenceStream.SentenceParser.TreeElement;

/**
 * Tests the leaf lines with an equals sign in the tag, as found in the FlorestaVirgem corpus.
 */
public class ADSentenceParserTest {

  private final SentenceParser parser = new SentenceParser();

  /**
   * A leaf with an equals sign and a colon in its tag gets the functional tag after the last
   * colon, as a regular leaf does. The lines are from the FlorestaVirgem corpus.
   */
  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "=H==CJT:num(\"818-5817\" <cjt-X> <card> <NER:virtual> M/F P)\t818-5817|2|H==CJT|num|818-5817",
      // a hyphen before the tag counts as a level marker, so these are regular leaves
      "==-=CJT:v-fin(\"voltar\" <vpcjt>  <fmc> <mv> PR 1S IND VFIN)\tvolto|5|CJT|v-fin|volto",
      "=-=CJT:v-fin(\"voltar\" <vpcjt>  <fmc> <mv> PR 3P IND VFIN)\tvoltam|4|CJT|v-fin|voltam",
      "=====N\u00A4=]:prop(\"Cia.\" <org> <np-close> F S)\tCia.|6|N\u00A4=]|prop|Cia."
  })
  void testLeafWithEqualsSignInTagKeepsItsFunctionalTag(String line, int level,
      String syntacticTag, String functionalTag, String lexeme) {
    TreeElement element = parser.getElement(line);
    Assertions.assertTrue(element.isLeaf());
    Leaf leaf = (Leaf) element;
    Assertions.assertEquals(level, leaf.getLevel());
    Assertions.assertEquals(syntacticTag, leaf.getSyntacticTag());
    Assertions.assertEquals(functionalTag, leaf.getFunctionalTag());
    Assertions.assertEquals(lexeme, leaf.getLexeme());
  }

  /** A leaf with an equals sign but no colon in its tag has no functional tag. */
  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "=a=b(\"x\" M S)\tx|2|a=b|x",
      "=H=CJT:(\"y\" M S)\ty|2|H=CJT:|y"
  })
  void testLeafWithEqualsSignAndNoFunctionalTag(String line, int level, String syntacticTag,
      String lexeme) {
    TreeElement element = parser.getElement(line);
    Assertions.assertTrue(element.isLeaf());
    Leaf leaf = (Leaf) element;
    Assertions.assertEquals(level, leaf.getLevel());
    Assertions.assertEquals(syntacticTag, leaf.getSyntacticTag());
    Assertions.assertNull(leaf.getFunctionalTag());
    Assertions.assertEquals(lexeme, leaf.getLexeme());
  }
}
