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

package opennlp.tools.util.featuregen;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class StringPatternTest {

  @Test
  void testIsAllLetters() {
    Assertions.assertTrue(StringPattern.recognize("test").isAllLetter());
    Assertions.assertTrue(StringPattern.recognize("TEST").isAllLetter());
    Assertions.assertTrue(StringPattern.recognize("TesT").isAllLetter());
    Assertions.assertTrue(StringPattern.recognize("grün").isAllLetter());
    Assertions.assertTrue(StringPattern.recognize("üäöæß").isAllLetter());
    Assertions.assertTrue(StringPattern.recognize("あア亜Ａａ").isAllLetter());
  }

  @Test
  void testIsInitialCapitalLetter() {
    Assertions.assertTrue(StringPattern.recognize("Test").isInitialCapitalLetter());
    Assertions.assertFalse(StringPattern.recognize("tEST").isInitialCapitalLetter());
    Assertions.assertTrue(StringPattern.recognize("TesT").isInitialCapitalLetter());
    Assertions.assertTrue(StringPattern.recognize("Üäöæß").isInitialCapitalLetter());
    Assertions.assertFalse(StringPattern.recognize("いイ井").isInitialCapitalLetter());
    Assertions.assertTrue(StringPattern.recognize("Iいイ井").isInitialCapitalLetter());
    Assertions.assertTrue(StringPattern.recognize("Ｉいイ井").isInitialCapitalLetter());
  }

  @Test
  void testIsAllCapitalLetter() {
    Assertions.assertTrue(StringPattern.recognize("TEST").isAllCapitalLetter());
    Assertions.assertTrue(StringPattern.recognize("ÄÄÄÜÜÜÖÖÖÖ").isAllCapitalLetter());
    Assertions.assertFalse(StringPattern.recognize("ÄÄÄÜÜÜÖÖä").isAllCapitalLetter());
    Assertions.assertFalse(StringPattern.recognize("ÄÄÄÜÜdÜÖÖ").isAllCapitalLetter());
    Assertions.assertTrue(StringPattern.recognize("ＡＢＣ").isAllCapitalLetter());
    Assertions.assertFalse(StringPattern.recognize("うウ宇").isAllCapitalLetter());
  }

  @Test
  void testIsAllLowerCaseLetter() {
    Assertions.assertTrue(StringPattern.recognize("test").isAllLowerCaseLetter());
    Assertions.assertTrue(StringPattern.recognize("öäü").isAllLowerCaseLetter());
    Assertions.assertTrue(StringPattern.recognize("öäüßßß").isAllLowerCaseLetter());
    Assertions.assertFalse(StringPattern.recognize("Test").isAllLowerCaseLetter());
    Assertions.assertFalse(StringPattern.recognize("TEST").isAllLowerCaseLetter());
    Assertions.assertFalse(StringPattern.recognize("testT").isAllLowerCaseLetter());
    Assertions.assertFalse(StringPattern.recognize("tesÖt").isAllLowerCaseLetter());
    Assertions.assertTrue(StringPattern.recognize("ａｂｃ").isAllLowerCaseLetter());
    Assertions.assertFalse(StringPattern.recognize("えエ絵").isAllLowerCaseLetter());
  }

  @Test
  void testIsAllDigit() {
    Assertions.assertTrue(StringPattern.recognize("123456").isAllDigit());
    Assertions.assertFalse(StringPattern.recognize("123,56").isAllDigit());
    Assertions.assertFalse(StringPattern.recognize("12356f").isAllDigit());
    Assertions.assertTrue(StringPattern.recognize("１２３４５６").isAllDigit());
  }

  @Test
  void testIsAllHiragana() {
    Assertions.assertTrue(StringPattern.recognize("あぱっち・るしーん").isAllHiragana());
    Assertions.assertFalse(StringPattern.recognize("あぱっち・そふとうぇあ財団").isAllHiragana());
    Assertions.assertFalse(StringPattern.recognize("あぱっち・るしーんＶ１．０").isAllHiragana());
  }

  @Test
  void testIsAllKatakana() {
    Assertions.assertTrue(StringPattern.recognize("アパッチ・ルシーン").isAllKatakana());
    Assertions.assertFalse(StringPattern.recognize("アパッチ・ソフトウェア財団").isAllKatakana());
    Assertions.assertFalse(StringPattern.recognize("アパッチ・ルシーンＶ１．０").isAllKatakana());
  }

  @Test
  void testDigits() {
    Assertions.assertEquals(6, StringPattern.recognize("123456").digits());
    Assertions.assertEquals(3, StringPattern.recognize("123fff").digits());
    Assertions.assertEquals(0, StringPattern.recognize("test").digits());
    Assertions.assertEquals(3, StringPattern.recognize("１２３ｆｆｆ").digits());
  }

  @Test
  void testContainsPeriod() {
    Assertions.assertTrue(StringPattern.recognize("test.").containsPeriod());
    Assertions.assertTrue(StringPattern.recognize("23.5").containsPeriod());
    Assertions.assertFalse(StringPattern.recognize("test,/-1").containsPeriod());
  }

  @Test
  void testContainsComma() {
    Assertions.assertTrue(StringPattern.recognize("test,").containsComma());
    Assertions.assertTrue(StringPattern.recognize("23,5").containsComma());
    Assertions.assertFalse(StringPattern.recognize("test./-1").containsComma());
  }

  @Test
  void testContainsSlash() {
    Assertions.assertTrue(StringPattern.recognize("test/").containsSlash());
    Assertions.assertTrue(StringPattern.recognize("23/5").containsSlash());
    Assertions.assertFalse(StringPattern.recognize("test.1-,").containsSlash());
  }

  @Test
  void testContainsDigit() {
    Assertions.assertTrue(StringPattern.recognize("test1").containsDigit());
    Assertions.assertTrue(StringPattern.recognize("23,5").containsDigit());
    Assertions.assertFalse(StringPattern.recognize("test./-,").containsDigit());
    Assertions.assertTrue(StringPattern.recognize("テスト１").containsDigit());
    Assertions.assertFalse(StringPattern.recognize("テストＴＥＳＴ").containsDigit());
  }

  @Test
  void testContainsHyphen() {
    Assertions.assertTrue(StringPattern.recognize("test--").containsHyphen());
    Assertions.assertTrue(StringPattern.recognize("23-5").containsHyphen());
    Assertions.assertFalse(StringPattern.recognize("test.1/,").containsHyphen());
  }

  @Test
  void testContainsLetters() {
    Assertions.assertTrue(StringPattern.recognize("test--").containsLetters());
    Assertions.assertTrue(StringPattern.recognize("23h5ßm").containsLetters());
    Assertions.assertFalse(StringPattern.recognize("---.1/,").containsLetters());
  }

}
