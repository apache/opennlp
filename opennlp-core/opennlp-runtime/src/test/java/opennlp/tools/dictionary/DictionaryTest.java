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

package opennlp.tools.dictionary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.StringList;

/**
 * Tests for the {@link Dictionary} class.
 */
public class DictionaryTest {

  /**
   * @return a case sensitive Dictionary
   */
  private Dictionary getCaseSensitive() {
    return new Dictionary(true);
  }

  /**
   * @return a case insensitive Dictionary
   */
  private Dictionary getCaseInsensitive() {
    return new Dictionary(false);
  }

  /**
   * Tests a basic lookup.
   */
  @Test
  void testLookup() {

    StringList entry1 = new StringList("1a", "1b");
    StringList entry1u = new StringList("1A", "1B");
    StringList entry2 = new StringList("1A", "1C");

    Dictionary dict = getCaseInsensitive();

    dict.put(entry1);

    Assertions.assertTrue(dict.contains(entry1));
    Assertions.assertTrue(dict.contains(entry1u));
    Assertions.assertFalse(dict.contains(entry2));
  }

  /**
   * Test lookup with case sensitive dictionary
   */
  @Test
  void testLookupCaseSensitive() {
    StringList entry1 = new StringList("1a", "1b");
    StringList entry1u = new StringList("1A", "1B");
    StringList entry2 = new StringList("1A", "1C");

    Dictionary dict = getCaseSensitive();

    dict.put(entry1);

    Assertions.assertTrue(dict.contains(entry1));
    Assertions.assertFalse(dict.contains(entry1u));
    Assertions.assertFalse(dict.contains(entry2));
  }

  /**
   * Tests serialization and deserailization of the {@link Dictionary}.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  @Test
  void testSerialization() throws IOException {
    Dictionary reference = getCaseInsensitive();

    String a1 = "a1";
    String a2 = "a2";
    String a3 = "a3";
    String a5 = "a5";

    reference.put(new StringList(a1, a2, a3, a5));

    ByteArrayOutputStream out = new ByteArrayOutputStream();

    reference.serialize(out);

    Dictionary recreated = new Dictionary(
        new ByteArrayInputStream(out.toByteArray()));

    Assertions.assertEquals(reference, recreated);
  }

  /**
   * Tests for the {@link Dictionary#parseOneEntryPerLine(java.io.Reader)}
   * method.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  @Test
  void testParseOneEntryPerLine() throws IOException {

    String testDictionary = "1a 1b 1c 1d \n 2a 2b 2c \n 3a \n 4a    4b   ";

    Dictionary dictionay =
        Dictionary.parseOneEntryPerLine(new StringReader(testDictionary));

    Assertions.assertEquals(4, dictionay.size());
    Assertions.assertTrue(dictionay.contains(new StringList("1a", "1b", "1c", "1d")));
    Assertions.assertTrue(dictionay.contains(new StringList("2a", "2b", "2c")));
    Assertions.assertTrue(dictionay.contains(new StringList(new String[] {"3a"})));
    Assertions.assertTrue(dictionay.contains(new StringList("4a", "4b")));
  }

  /**
   * Tests for the {@link Dictionary#equals(Object)} method.
   */
  @Test
  void testEquals() {
    StringList entry1 = new StringList("1a", "1b");
    StringList entry2 = new StringList("2a", "2b");

    Dictionary dictA = getCaseInsensitive();
    dictA.put(entry1);
    dictA.put(entry2);

    Dictionary dictB = getCaseInsensitive();
    dictB.put(entry1);
    dictB.put(entry2);

    Dictionary dictC = getCaseSensitive();
    dictC.put(entry1);
    dictC.put(entry2);

    Assertions.assertEquals(dictA, dictB);
    Assertions.assertEquals(dictC, dictA);
    Assertions.assertEquals(dictB, dictC);
  }

  /**
   * Tests the {@link Dictionary#hashCode()} method.
   */
  @Test
  void testHashCode() {
    StringList entry1 = new StringList("1a", "1b");
    StringList entry2 = new StringList("1A", "1B");

    Dictionary dictA = getCaseInsensitive();
    dictA.put(entry1);

    Dictionary dictB = getCaseInsensitive();
    dictB.put(entry2);

    Dictionary dictC = getCaseSensitive();
    dictC.put(entry1);

    Dictionary dictD = getCaseSensitive();
    dictD.put(entry2);

    Assertions.assertEquals(dictA.hashCode(), dictB.hashCode());
    Assertions.assertEquals(dictB.hashCode(), dictC.hashCode());
    Assertions.assertEquals(dictC.hashCode(), dictD.hashCode());
  }

  /**
   * Tests for the {@link Dictionary#toString()} method.
   */
  @Test
  void testToString() {
    StringList entry1 = new StringList("1a", "1b");

    Dictionary dictA = getCaseInsensitive();

    dictA.toString();

    dictA.put(entry1);

    dictA.toString();
  }

  /**
   * Tests the lookup of tokens of different case.
   */
  @Test
  void testDifferentCaseLookup() {

    StringList entry1 = new StringList("1a", "1b");
    StringList entry2 = new StringList("1A", "1B");

    Dictionary dict = getCaseInsensitive();

    dict.put(entry1);

    Assertions.assertTrue(dict.contains(entry2));
  }

  /**
   * Tests the lookup of tokens of different case.
   */
  @Test
  void testDifferentCaseLookupCaseSensitive() {

    StringList entry1 = new StringList("1a", "1b");
    StringList entry2 = new StringList("1A", "1B");

    Dictionary dict = getCaseSensitive();

    dict.put(entry1);

    Assertions.assertFalse(dict.contains(entry2));
  }

}
