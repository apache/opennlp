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

import org.junit.Assert;
import org.junit.Test;

public class StringPatternTest {

  @Test
  public void testIsAllLetters() {
    Assert.assertTrue(StringPattern.recognize("test").isAllLetter());
    Assert.assertTrue(StringPattern.recognize("TEST").isAllLetter());
    Assert.assertTrue(StringPattern.recognize("TesT").isAllLetter());
    Assert.assertTrue(StringPattern.recognize("grün").isAllLetter());
    Assert.assertTrue(StringPattern.recognize("üäöæß").isAllLetter());
  }

  @Test
  public void testIsInitialCapitalLetter() {
    Assert.assertTrue(StringPattern.recognize("Test").isInitialCapitalLetter());
    Assert.assertFalse(StringPattern.recognize("tEST").isInitialCapitalLetter());
    Assert.assertTrue(StringPattern.recognize("TesT").isInitialCapitalLetter());
    Assert.assertTrue(StringPattern.recognize("Üäöæß").isInitialCapitalLetter());
  }

  @Test
  public void testIsAllCapitalLetter() {
    Assert.assertTrue(StringPattern.recognize("TEST").isAllCapitalLetter());
    Assert.assertTrue(StringPattern.recognize("ÄÄÄÜÜÜÖÖÖÖ").isAllCapitalLetter());
    Assert.assertFalse(StringPattern.recognize("ÄÄÄÜÜÜÖÖä").isAllCapitalLetter());
    Assert.assertFalse(StringPattern.recognize("ÄÄÄÜÜdÜÖÖ").isAllCapitalLetter());
  }

  @Test
  public void testIsAllLowerCaseLetter() {
    Assert.assertTrue(StringPattern.recognize("test").isAllLowerCaseLetter());
    Assert.assertTrue(StringPattern.recognize("öäü").isAllLowerCaseLetter());
    Assert.assertTrue(StringPattern.recognize("öäüßßß").isAllLowerCaseLetter());
    Assert.assertFalse(StringPattern.recognize("Test").isAllLowerCaseLetter());
    Assert.assertFalse(StringPattern.recognize("TEST").isAllLowerCaseLetter());
    Assert.assertFalse(StringPattern.recognize("testT").isAllLowerCaseLetter());
    Assert.assertFalse(StringPattern.recognize("tesÖt").isAllLowerCaseLetter());
  }

  @Test
  public void testIsAllDigit() {
    Assert.assertTrue(StringPattern.recognize("123456").isAllDigit());
    Assert.assertFalse(StringPattern.recognize("123,56").isAllDigit());
    Assert.assertFalse(StringPattern.recognize("12356f").isAllDigit());
  }

  @Test
  public void testDigits() {
    Assert.assertEquals(6, StringPattern.recognize("123456").digits());
    Assert.assertEquals(3, StringPattern.recognize("123fff").digits());
    Assert.assertEquals(0, StringPattern.recognize("test").digits());
  }

  @Test
  public void testContainsPeriod() {
    Assert.assertTrue(StringPattern.recognize("test.").containsPeriod());
    Assert.assertTrue(StringPattern.recognize("23.5").containsPeriod());
    Assert.assertFalse(StringPattern.recognize("test,/-1").containsPeriod());
  }

  @Test
  public void testContainsComma() {
    Assert.assertTrue(StringPattern.recognize("test,").containsComma());
    Assert.assertTrue(StringPattern.recognize("23,5").containsComma());
    Assert.assertFalse(StringPattern.recognize("test./-1").containsComma());
  }

  @Test
  public void testContainsSlash() {
    Assert.assertTrue(StringPattern.recognize("test/").containsSlash());
    Assert.assertTrue(StringPattern.recognize("23/5").containsSlash());
    Assert.assertFalse(StringPattern.recognize("test.1-,").containsSlash());
  }

  @Test
  public void testContainsDigit() {
    Assert.assertTrue(StringPattern.recognize("test1").containsDigit());
    Assert.assertTrue(StringPattern.recognize("23,5").containsDigit());
    Assert.assertFalse(StringPattern.recognize("test./-,").containsDigit());
  }

  @Test
  public void testContainsHyphen() {
    Assert.assertTrue(StringPattern.recognize("test--").containsHyphen());
    Assert.assertTrue(StringPattern.recognize("23-5").containsHyphen());
    Assert.assertFalse(StringPattern.recognize("test.1/,").containsHyphen());
  }

  @Test
  public void testContainsLetters() {
    Assert.assertTrue(StringPattern.recognize("test--").containsLetters());
    Assert.assertTrue(StringPattern.recognize("23h5ßm").containsLetters());
    Assert.assertFalse(StringPattern.recognize("---.1/,").containsLetters());
  }

}
