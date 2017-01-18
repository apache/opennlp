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

import org.junit.Assert;
import org.junit.Test;

import opennlp.tools.util.InvalidFormatException;
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
  public void testLookup() {

    StringList entry1 = new StringList("1a", "1b");
    StringList entry1u = new StringList("1A", "1B");
    StringList entry2 = new StringList("1A", "1C");

    Dictionary dict = getCaseInsensitive();

    dict.put(entry1);

    Assert.assertTrue(dict.contains(entry1));
    Assert.assertTrue(dict.contains(entry1u));
    Assert.assertTrue(!dict.contains(entry2));
  }

  /**
   * Test lookup with case sensitive dictionary
   */
  @Test
  public void testLookupCaseSensitive() {
    StringList entry1 = new StringList("1a", "1b");
    StringList entry1u = new StringList("1A", "1B");
    StringList entry2 = new StringList("1A", "1C");

    Dictionary dict = getCaseSensitive();

    dict.put(entry1);

    Assert.assertTrue(dict.contains(entry1));
    Assert.assertTrue(!dict.contains(entry1u));
    Assert.assertTrue(!dict.contains(entry2));
  }

  /**
   * Tests serialization and deserailization of the {@link Dictionary}.
   *
   * @throws IOException
   * @throws InvalidFormatException
   */
  @Test
  public void testSerialization() throws IOException {
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

    Assert.assertTrue(reference.equals(recreated));
  }

  /**
   * Tests for the {@link Dictionary#parseOneEntryPerLine(java.io.Reader)}
   * method.
   *
   * @throws IOException
   */
  @Test
  public void testParseOneEntryPerLine() throws IOException {

    String testDictionary = "1a 1b 1c 1d \n 2a 2b 2c \n 3a \n 4a    4b   ";

    Dictionary dictionay =
        Dictionary.parseOneEntryPerLine(new StringReader(testDictionary));

    Assert.assertTrue(dictionay.size() == 4);
    Assert.assertTrue(dictionay.contains(new StringList("1a", "1b", "1c", "1d")));
    Assert.assertTrue(dictionay.contains(new StringList("2a", "2b", "2c")));
    Assert.assertTrue(dictionay.contains(new StringList(new String[]{"3a"})));
    Assert.assertTrue(dictionay.contains(new StringList("4a", "4b")));
  }

  /**
   * Tests for the {@link Dictionary#equals(Object)} method.
   */
  @Test
  public void testEquals() {
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

    Assert.assertTrue(dictA.equals(dictB));
    Assert.assertTrue(dictC.equals(dictA));
    Assert.assertTrue(dictB.equals(dictC));
  }

  /**
   * Tests the {@link Dictionary#hashCode()} method.
   */
  @Test
  public void testHashCode() {
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

    Assert.assertEquals(dictA.hashCode(), dictB.hashCode());
    Assert.assertEquals(dictB.hashCode(), dictC.hashCode());
    Assert.assertEquals(dictC.hashCode(), dictD.hashCode());
  }

  /**
   * Tests for the {@link Dictionary#toString()} method.
   */
  @Test
  public void testToString() {
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
  public void testDifferentCaseLookup() {

    StringList entry1 = new StringList("1a", "1b");
    StringList entry2 = new StringList("1A", "1B");

    Dictionary dict = getCaseInsensitive();

    dict.put(entry1);

    Assert.assertTrue(dict.contains(entry2));
  }

  /**
   * Tests the lookup of tokens of different case.
   */
  @Test
  public void testDifferentCaseLookupCaseSensitive() {

    StringList entry1 = new StringList("1a", "1b");
    StringList entry2 = new StringList("1A", "1B");

    Dictionary dict = getCaseSensitive();

    dict.put(entry1);

    Assert.assertTrue(!dict.contains(entry2));
  }

}