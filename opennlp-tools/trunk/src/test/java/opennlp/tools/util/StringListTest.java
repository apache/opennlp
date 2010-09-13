/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.junit.Test;

/**
 * Tests for the {@link StringList} class.
 */
public class StringListTest {

  /**
   * Tests {@link StringList#getToken(int)}.
   */
  @Test
  public void testGetToken() {
    StringList l = new StringList("a", "b");

    assertEquals(2, l.size());

    assertEquals("a", l.getToken(0));
    assertEquals("b", l.getToken(1));
  }

  /**
   * Tests {@link StringList#iterator()}.
   */
  @Test
  public void testItertor() {
    StringList l = new StringList("a");

    Iterator<String> it = l.iterator();

    assertTrue(it.hasNext());
    assertEquals("a", it.next());
    assertFalse(it.hasNext());

    // now test with more than one string
    l = new StringList("a", "b", "c");
    it = l.iterator();

    assertTrue(it.hasNext());
    assertEquals("a", it.next());

    assertTrue(it.hasNext());
    assertEquals("b", it.next());

    assertTrue(it.hasNext());
    assertEquals("c", it.next());

    assertFalse(it.hasNext());
  }

  /**
   * Tests {@link StringList#compareToIgnoreCase(StringList)}.
   */
  @Test
  public void testCompareToIgnoreCase() {
    assertTrue(new StringList("a", "b").compareToIgnoreCase(
        new StringList("A", "B")));
  }

  /**
   * Tests {@link StringList#equals(Object)}.
   */
  @Test
  public void testEquals() {
    assertEquals(new StringList("a", "b"),
        new StringList("a", "b"));

    assertFalse(new StringList("a", "b").equals(
        new StringList("A", "B")));
  }

  /**
   * Tests {@link StringList#hashCode()}.
   */
  @Test
  public void testHashCode() {
    assertEquals(new StringList("a", "b").hashCode(),
        new StringList("a", "b").hashCode());
  }

  /**
   * Tests {@link StringList#toString()}.
   */
  @Test
  public void testToString() {
    new StringList("a", "b").toString();
  }
}