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

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link StringList} class.
 */
public class StringListTest {

  /**
   * Tests {@link StringList} which uses {@link String#intern}.
   */
  @Test
  public void testIntern() {
    StringList l1 = new StringList("a");
    StringList l2 = new StringList("a", "b");
    Assert.assertTrue(l1.getToken(0) == l2.getToken(0));
  }

  /**
   * Tests {@link StringList#getToken(int)}.
   */
  @Test
  public void testGetToken() {
    StringList l = new StringList("a", "b");
    Assert.assertEquals(2, l.size());
    Assert.assertEquals("a", l.getToken(0));
    Assert.assertEquals("b", l.getToken(1));
  }

  /**
   * Tests {@link StringList#iterator()}.
   */
  @Test
  public void testIterator() {
    StringList l = new StringList("a");
    Iterator<String> it = l.iterator();
    Assert.assertTrue(it.hasNext());
    Assert.assertEquals("a", it.next());
    Assert.assertFalse(it.hasNext());

    // now test with more than one string
    l = new StringList("a", "b", "c");
    it = l.iterator();

    Assert.assertTrue(it.hasNext());
    Assert.assertEquals("a", it.next());
    Assert.assertTrue(it.hasNext());
    Assert.assertEquals("b", it.next());
    Assert.assertTrue(it.hasNext());
    Assert.assertEquals("c", it.next());
    Assert.assertFalse(it.hasNext());
  }

  /**
   * Tests {@link StringList#compareToIgnoreCase(StringList)}.
   */
  @Test
  public void testCompareToIgnoreCase() {
    Assert.assertTrue(new StringList("a", "b").compareToIgnoreCase(
        new StringList("A", "B")));
  }

  /**
   * Tests {@link StringList#equals(Object)}.
   */
  @Test
  public void testEquals() {
    Assert.assertEquals(new StringList("a", "b"),
        new StringList("a", "b"));

    Assert.assertFalse(new StringList("a", "b").equals(
        new StringList("A", "B")));
  }

  /**
   * Tests {@link StringList#hashCode()}.
   */
  @Test
  public void testHashCode() {
    Assert.assertEquals(new StringList("a", "b").hashCode(),
        new StringList("a", "b").hashCode());
    Assert.assertNotEquals(new StringList("a", "b").hashCode(),
        new StringList("a", "c").hashCode());
  }

  /**
   * Tests {@link StringList#toString()}.
   */
  @Test
  public void testToString() {
    Assert.assertEquals("[a]", new StringList("a").toString());
    Assert.assertEquals("[a,b]", new StringList("a", "b").toString());
  }
}
