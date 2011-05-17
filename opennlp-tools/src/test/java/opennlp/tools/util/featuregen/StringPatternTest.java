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

import static org.junit.Assert.*;

import org.junit.Test;

public class StringPatternTest {

  @Test
  public void testIsAllLetters() {
    assertTrue(StringPattern.recognize("test").isAllLetter());
    assertTrue(StringPattern.recognize("TEST").isAllLetter());
    assertTrue(StringPattern.recognize("TesT").isAllLetter());
    assertTrue(StringPattern.recognize("grün").isAllLetter());
    assertTrue(StringPattern.recognize("üäöæß").isAllLetter());
  }
  
  @Test
  public void testIsInitialCapitalLetter() {
    assertTrue(StringPattern.recognize("Test").isInitialCapitalLetter());
    assertFalse(StringPattern.recognize("tEST").isInitialCapitalLetter());
    assertTrue(StringPattern.recognize("TesT").isInitialCapitalLetter());
    assertTrue(StringPattern.recognize("Üäöæß").isInitialCapitalLetter());
  }
  
  @Test
  public void testIsAllCapitalLetter() {
    assertTrue(StringPattern.recognize("TEST").isAllCapitalLetter());
    assertTrue(StringPattern.recognize("ÄÄÄÜÜÜÖÖÖÖ").isAllCapitalLetter());
    assertFalse(StringPattern.recognize("ÄÄÄÜÜÜÖÖä").isAllCapitalLetter());
    assertFalse(StringPattern.recognize("ÄÄÄÜÜdÜÖÖ").isAllCapitalLetter());
  }
  
  @Test
  public void testIsAllLowerCaseLetter() {
    assertTrue(StringPattern.recognize("test").isAllLowerCaseLetter());
    assertTrue(StringPattern.recognize("öäü").isAllLowerCaseLetter());
    assertTrue(StringPattern.recognize("öäüßßß").isAllLowerCaseLetter());
    assertFalse(StringPattern.recognize("Test").isAllLowerCaseLetter());
    assertFalse(StringPattern.recognize("TEST").isAllLowerCaseLetter());
    assertFalse(StringPattern.recognize("testT").isAllLowerCaseLetter());
    assertFalse(StringPattern.recognize("tesÖt").isAllLowerCaseLetter());
  }
  
  @Test
  public void testIsAllDigit() {
    assertTrue(StringPattern.recognize("123456").isAllDigit());
    assertFalse(StringPattern.recognize("123,56").isAllDigit());
    assertFalse(StringPattern.recognize("12356f").isAllDigit());
  }
  
  @Test
  public void testDigits() {
    assertEquals(6, StringPattern.recognize("123456").digits());
    assertEquals(3, StringPattern.recognize("123fff").digits());
    assertEquals(0, StringPattern.recognize("test").digits());
  }
  
  @Test
  public void testContainsPeriod() {
    assertTrue(StringPattern.recognize("test.").containsPeriod());
    assertTrue(StringPattern.recognize("23.5").containsPeriod());
    assertFalse(StringPattern.recognize("test,/-1").containsPeriod());
  }
  
  @Test
  public void testContainsComma() {
    assertTrue(StringPattern.recognize("test,").containsComma());
    assertTrue(StringPattern.recognize("23,5").containsComma());
    assertFalse(StringPattern.recognize("test./-1").containsComma());
  }
  
  @Test
  public void testContainsSlash() {
    assertTrue(StringPattern.recognize("test/").containsSlash());
    assertTrue(StringPattern.recognize("23/5").containsSlash());
    assertFalse(StringPattern.recognize("test.1-,").containsSlash());
  }
  
  @Test
  public void testContainsDigit() {
    assertTrue(StringPattern.recognize("test1").containsDigit());
    assertTrue(StringPattern.recognize("23,5").containsDigit());
    assertFalse(StringPattern.recognize("test./-,").containsDigit());
  }

  @Test
  public void testContainsHyphen() {
    assertTrue(StringPattern.recognize("test--").containsHyphen());
    assertTrue(StringPattern.recognize("23-5").containsHyphen());
    assertFalse(StringPattern.recognize("test.1/,").containsHyphen());
  }
  
  @Test
  public void testContainsLetters() {
    assertTrue(StringPattern.recognize("test--").containsLetters());
    assertTrue(StringPattern.recognize("23h5ßm").containsLetters());
    assertFalse(StringPattern.recognize("---.1/,").containsLetters());
  }
  
}
