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

package opennlp.spellcheck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SuggestItemTest {

  @Test
  void smallerEditDistanceSortsFirst() {
    final SuggestItem near = new SuggestItem("the", 1, 10L);
    final SuggestItem far = new SuggestItem("then", 2, 1_000_000L);
    assertTrue(near.compareTo(far) < 0, "lower edit distance wins regardless of frequency");
    assertTrue(far.compareTo(near) > 0);
  }

  @Test
  void onEqualDistanceHigherFrequencySortsFirst() {
    final SuggestItem common = new SuggestItem("the", 1, 1_000_000L);
    final SuggestItem rare = new SuggestItem("she", 1, 5L);
    assertTrue(common.compareTo(rare) < 0, "higher frequency wins on a distance tie");
    assertTrue(rare.compareTo(common) > 0);
  }

  @Test
  void identicalFieldsCompareEqual() {
    final SuggestItem a = new SuggestItem("word", 1, 42L);
    final SuggestItem b = new SuggestItem("word", 1, 42L);
    assertEquals(0, a.compareTo(b));
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void naturalOrderProducesBestFirst() {
    final List<SuggestItem> items = new ArrayList<>(List.of(
        new SuggestItem("c", 2, 999L),
        new SuggestItem("a", 0, 1L),
        new SuggestItem("b1", 1, 5L),
        new SuggestItem("b2", 1, 500L)));
    Collections.sort(items);
    assertEquals(List.of("a", "b2", "b1", "c"),
        items.stream().map(SuggestItem::term).toList());
  }

  @Test
  void accessorsExposeRecordComponents() {
    final SuggestItem item = new SuggestItem("hello", 2, 4569100L);
    assertEquals("hello", item.term());
    assertEquals(2, item.editDistance());
    assertEquals(4569100L, item.frequency());
  }

  @Test
  void termBreaksTiesSoOrderingIsConsistentWithEquals() {
    // Same distance and frequency but different terms: must NOT compare equal, otherwise
    // a sorted set would silently drop one of them.
    final SuggestItem cat = new SuggestItem("cat", 1, 5L);
    final SuggestItem car = new SuggestItem("car", 1, 5L);
    assertTrue(cat.compareTo(car) != 0, "distinct terms must not compare equal");
    assertTrue(Integer.signum(cat.compareTo(car)) == -Integer.signum(car.compareTo(cat)),
        "comparison must be antisymmetric");

    final TreeSet<SuggestItem> set = new TreeSet<>(List.of(cat, car));
    assertEquals(2, set.size(), "neither distinct suggestion may be dropped from a TreeSet");
  }

  @Test
  void nullTermIsRejected() {
    assertThrows(NullPointerException.class, () -> new SuggestItem(null, 0, 1L));
  }
}
