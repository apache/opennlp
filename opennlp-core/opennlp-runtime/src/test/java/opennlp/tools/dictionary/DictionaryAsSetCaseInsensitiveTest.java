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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.StringList;

public class DictionaryAsSetCaseInsensitiveTest {

  private Dictionary getDict() {
    return new Dictionary(false);
  }

  private StringList asSL(String str) {
    return new StringList(str);
  }

  /**
   * Tests a basic lookup.
   */
  @Test
  void testLookup() {

    String a = "a";
    String b = "b";

    Dictionary dict = getDict();

    dict.put(asSL(a));

    Set<String> set = dict.asStringSet();

    Assertions.assertTrue(set.contains(a));
    Assertions.assertFalse(set.contains(b));

    Assertions.assertTrue(set.contains(a.toUpperCase()));
  }

  /**
   * Tests set.
   */
  @Test
  void testSet() {

    String a = "a";
    String a1 = "a";

    Dictionary dict = getDict();

    dict.put(asSL(a));
    dict.put(asSL(a1));

    Set<String> set = dict.asStringSet();

    Assertions.assertTrue(set.contains(a));
    Assertions.assertEquals(1, set.size());
  }

  /**
   * Tests set.
   */
  @Test
  void testSetDiffCase() {

    String a = "a";
    String a1 = "A";

    Dictionary dict = getDict();

    dict.put(asSL(a));
    dict.put(asSL(a1));

    Set<String> set = dict.asStringSet();

    Assertions.assertTrue(set.contains(a));
    Assertions.assertEquals(1, set.size());
  }

  /**
   * Tests for the {@link Dictionary#equals(Object)} method.
   */
  @Test
  void testEquals() {
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

    Assertions.assertEquals(setA.size(), setB.size());
    Assertions.assertTrue(setA.containsAll(setB));
  }

  /**
   * Tests for the {@link Dictionary#equals(Object)} method.
   */
  @Test
  void testEqualsDifferentCase() {

    Dictionary dictA = getDict();
    dictA.put(asSL("1a"));
    dictA.put(asSL("1b"));

    Set<String> setA = dictA.asStringSet();

    Dictionary dictB = getDict();
    dictB.put(asSL("1A"));
    dictB.put(asSL("1B"));

    Set<String> setB = dictB.asStringSet();

    Assertions.assertEquals(setA.size(), setB.size());
    Assertions.assertTrue(setA.containsAll(setB));
  }

  /**
   * Tests the {@link Dictionary#asStringSet()#hashCode()} method without case changes.
   */
  @Test
  void testHashCode() {
    String entry1 = "a1";

    Dictionary dictA = getDict();
    dictA.put(asSL(entry1));

    Dictionary dictB = getDict();
    dictB.put(asSL(entry1));

    Assertions.assertEquals(dictA.hashCode(), dictB.hashCode());

    Set<String> setA = dictA.asStringSet();
    Set<String> setB = dictB.asStringSet();

    Assertions.assertEquals(setA.hashCode(), setB.hashCode());
  }

  /**
   * Tests the {@link Dictionary#asStringSet()#hashCode()}} method with case changes.
   */
  @Test
  void testHashCodeDifferentCase() {
    String entry1 = "a1";

    Dictionary dictA = getDict();
    dictA.put(asSL(entry1));

    Dictionary dictB = getDict();
    dictB.put(asSL(entry1.toUpperCase())); // adjusting entry to differ case-wise.

    Assertions.assertEquals(dictA.hashCode(), dictB.hashCode());

    Set<String> setA = dictA.asStringSet();
    Set<String> setB = dictB.asStringSet();

    Assertions.assertEquals(setA.hashCode(), setB.hashCode());
  }

  /**
   * Tests the lookup of tokens of different case.
   */
  @Test
  void testDifferentCaseLookup() {

    String entry1 = "1a";
    String entry2 = "1A";

    // create a case sensitive dictionary
    Dictionary dict = getDict();

    dict.put(asSL(entry1));

    Set<String> set = dict.asStringSet();

    Assertions.assertTrue(set.contains(entry2));
  }

  /**
   * Tests the iterator implementation
   */
  @Test
  void testIterator() {

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

    Assertions.assertEquals(2, elements.size());
    Assertions.assertTrue(elements.contains(entry1));
    Assertions.assertTrue(elements.contains(entry2));

  }
}
