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

package opennlp.tools.util;

import java.util.Iterator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link StringList} class.
 */
public class StringListTest {

  /**
   * Tests {@link StringList} which uses {@link String#intern}.
   */
  @Test
  void testIntern() {
    StringList l1 = new StringList("a");
    StringList l2 = new StringList("a", "b");
    Assertions.assertSame(l1.getToken(0), l2.getToken(0));
  }

  /**
   * Tests {@link StringList#getToken(int)}.
   */
  @Test
  void testGetToken() {
    StringList l = new StringList("a", "b");
    Assertions.assertEquals(2, l.size());
    Assertions.assertEquals("a", l.getToken(0));
    Assertions.assertEquals("b", l.getToken(1));
  }

  /**
   * Tests {@link StringList#iterator()}.
   */
  @Test
  void testIterator() {
    StringList l = new StringList("a");
    Iterator<String> it = l.iterator();
    Assertions.assertTrue(it.hasNext());
    Assertions.assertEquals("a", it.next());
    Assertions.assertFalse(it.hasNext());

    // now test with more than one string
    l = new StringList("a", "b", "c");
    it = l.iterator();

    Assertions.assertTrue(it.hasNext());
    Assertions.assertEquals("a", it.next());
    Assertions.assertTrue(it.hasNext());
    Assertions.assertEquals("b", it.next());
    Assertions.assertTrue(it.hasNext());
    Assertions.assertEquals("c", it.next());
    Assertions.assertFalse(it.hasNext());
  }

  /**
   * Tests {@link StringList#compareToIgnoreCase(StringList)}.
   */
  @Test
  void testCompareToIgnoreCase() {
    Assertions.assertTrue(new StringList("a", "b").compareToIgnoreCase(
        new StringList("A", "B")));
  }

  /**
   * Tests {@link StringList#equals(Object)}.
   */
  @Test
  void testEquals() {
    Assertions.assertEquals(new StringList("a", "b"),
        new StringList("a", "b"));

    Assertions.assertNotEquals(new StringList("a", "b"), new StringList("A", "B"));
  }

  /**
   * Tests {@link StringList#hashCode()}.
   */
  @Test
  void testHashCode() {
    Assertions.assertEquals(new StringList("a", "b").hashCode(),
        new StringList("a", "b").hashCode());
    Assertions.assertNotEquals(new StringList("a", "b").hashCode(),
        new StringList("a", "c").hashCode());
  }

  /**
   * Tests {@link StringList#toString()}.
   */
  @Test
  void testToString() {
    Assertions.assertEquals("[a]", new StringList("a").toString());
    Assertions.assertEquals("[a,b]", new StringList("a", "b").toString());
  }
}
