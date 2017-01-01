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

import opennlp.tools.util.StringList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class DictionaryAsSetCaseSensitiveTest {

  private Dictionary getDict() {
    return new Dictionary(true);
  }

  private StringList asSL(String str) {
    return new StringList(str);
  }

  /**
   * Tests a basic lookup.
   */
  @Test
  public void testLookup() {

    String a = "a";
    String b = "b";

    Dictionary dict = getDict();

    dict.put(asSL(a));

    Set<String> set = dict.asStringSet();

    assertTrue(set.contains(a));
    assertFalse(set.contains(b));

    assertFalse(set.contains(a.toUpperCase()));
  }

  /**
   * Tests set.
   */
  @Test
  public void testSet() {

    String a = "a";
    String a1 = "a";

    Dictionary dict = getDict();

    dict.put(asSL(a));
    dict.put(asSL(a1));

    Set<String> set = dict.asStringSet();

    assertTrue(set.contains(a));
    assertEquals(1, set.size());
  }

  /**
   * Tests set.
   */
  @Test
  public void testSetDiffCase() {

    String a = "a";
    String a1 = "A";

    Dictionary dict = getDict();

    dict.put(asSL(a));
    dict.put(asSL(a1));

    Set<String> set = dict.asStringSet();

    assertTrue(set.contains(a));
    assertEquals(2, set.size());
  }

  /**
   * Tests for the {@link Dictionary#equals(Object)} method.
   */
  @Test
  public void testEquals() {
    String entry1 = "1a";
    String entry2 = "1b";

    Dictionary dictA = getDict();
    dictA.put(asSL(entry1));
    dictA.put(asSL(entry2));

    Set<String> setA = dictA.asStringSet();

    Dictionary dictB = getDict();
    dictB.put(asSL(entry1));
    dictB.put(asSL(entry2));

    Set<String> setB = dictB.asStringSet();

    assertTrue(setA.equals(setB));
  }

  /**
   * Tests for the {@link Dictionary#equals(Object)} method.
   */
  @Test
  public void testEqualsDifferentCase() {

    Dictionary dictA = getDict();
    dictA.put(asSL("1a"));
    dictA.put(asSL("1b"));

    Set<String> setA = dictA.asStringSet();

    Dictionary dictB = getDict();
    dictB.put(asSL("1A"));
    dictB.put(asSL("1B"));

    Set<String> setB = dictB.asStringSet();

    // should fail in case sensitive dict
    assertFalse(setA.equals(setB));
  }

  /**
   * Tests the {@link Dictionary#hashCode()} method.
   */
  @Test
  public void testHashCode() {
    String entry1 = "a1";

    Dictionary dictA = getDict();
    dictA.put(asSL(entry1));

    Set<String> setA = dictA.asStringSet();

    Dictionary dictB = getDict();
    dictB.put(asSL(entry1));

    Set<String> setB = dictB.asStringSet();

    assertEquals(setA.hashCode(), setB.hashCode());
  }

  /**
   * Tests the {@link Dictionary#hashCode()} method.
   */
  @Test
  public void testHashCodeDifferentCase() {
    String entry1 = "a1";

    Dictionary dictA = getDict();
    dictA.put(asSL(entry1));

    Set<String> setA = dictA.asStringSet();

    Dictionary dictB = getDict();
    dictB.put(asSL(entry1.toUpperCase()));

    Set<String> setB = dictB.asStringSet();

    // TODO: should it be equal??
    assertNotSame(setA.hashCode(), setB.hashCode());
  }

  /**
   * Tests the lookup of tokens of different case.
   */
  @Test
  public void testDifferentCaseLookup() {

    String entry1 = "1a";
    String entry2 = "1A";

    // create a case sensitive dictionary
    Dictionary dict = getDict();

    dict.put(asSL(entry1));

    Set<String> set = dict.asStringSet();

    // should return false because 1a != 1A in a case sensitive lookup
    assertFalse(set.contains(entry2));
  }

  /**
   * Tests the iterator implementation
   */
  @Test
  public void testIterator() {

    String entry1 = "1a";
    String entry2 = "1b";

    Dictionary dictA = getDict();
    dictA.put(asSL(entry1));
    dictA.put(asSL(entry2));
    dictA.put(asSL(entry1.toUpperCase()));
    dictA.put(asSL(entry2.toUpperCase()));

    Iterator<String> it = dictA.asStringSet().iterator();
    List<String> elements = new ArrayList<>();
    while (it.hasNext()) {
      elements.add(it.next());
    }

    assertEquals(4, elements.size());
    assertTrue(elements.contains(entry1));
    assertTrue(elements.contains(entry2));
    assertTrue(elements.contains(entry1.toUpperCase()));
    assertTrue(elements.contains(entry2.toUpperCase()));

  }
}
