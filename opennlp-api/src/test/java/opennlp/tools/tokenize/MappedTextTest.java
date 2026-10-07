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
package opennlp.tools.tokenize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MappedTextTest {

  @Test
  void testNegativeCapacityIsRejected() {
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> new MappedText(-1));
    assertEquals("capacity must not be negative", e.getMessage());
  }

  @Test
  void testCapacityIsUsedAsGiven() {
    assertEquals(42, new MappedText(42).capacity());
  }

  @Test
  void testZeroCapacityGrowsOnFirstAdd() {
    final MappedText mapped = new MappedText(0);
    mapped.add('a', 0, 1);
    assertEquals(1, mapped.length);
    assertEquals("a", mapped.text());
  }

  @Test
  void testBuffersGrowBeyondTheInitialCapacity() {
    final MappedText mapped = new MappedText(1);
    for (int i = 0; i < 100; i++) {
      mapped.add((char) ('a' + i % 26), i, i + 1);
    }
    assertEquals(100, mapped.length);
    assertEquals(99, mapped.starts[99]);
    assertEquals(100, mapped.ends[99]);
  }

  @Test
  void testStringSharesOneSourceRange() {
    final MappedText mapped = new MappedText(4);
    mapped.add("ab", 3, 5);
    assertEquals("ab", mapped.text());
    assertEquals(3, mapped.starts[1]);
    assertEquals(5, mapped.ends[1]);
  }

  @Test
  void testSupplementaryCodePointRoundTrips() {
    final MappedText mapped = new MappedText(2);
    mapped.addCodePoint(0x1F600, 0, 2);
    assertEquals(2, mapped.length);
    assertEquals(0x1F600, mapped.codePointAt(0));
  }

  @Test
  void testUnpairedHighSurrogateIsReturnedAsIs() {
    final MappedText mapped = new MappedText(1);
    mapped.add('\uD83D', 0, 1);
    assertEquals(0xD83D, mapped.codePointAt(0));
  }

  @Test
  void testCodePointAtOutsideLengthIsRejected() {
    final MappedText mapped = new MappedText(8);
    mapped.add('a', 0, 1);
    assertThrows(IndexOutOfBoundsException.class, () -> mapped.codePointAt(1));
    assertThrows(IndexOutOfBoundsException.class, () -> mapped.codePointAt(-1));
  }
}
