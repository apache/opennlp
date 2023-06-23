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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Sequence} class.
 */
public class SequenceTest {

  /**
   * Tests the copy constructor {@link Sequence#Sequence(Sequence)}.
   */
  @Test
  void testCopyConstructor() {
    Sequence sequence = new Sequence();
    sequence.add("a", 10);
    sequence.add("b", 20);

    Sequence copy = new Sequence(sequence);

    Assertions.assertEquals(sequence.getOutcomes(), copy.getOutcomes());
    Assertions.assertArrayEquals(copy.getProbs(), sequence.getProbs(), 0.0);
    Assertions.assertEquals(0, sequence.compareTo(copy));
  }

  /**
   * Tests {@link Sequence#add(String, double)}, also
   * tests {@link Sequence#getOutcomes()} and {@link Sequence#getProbs()}.
   */
  @Test
  void testAddMethod() {
    Sequence sequence = new Sequence();
    sequence.add("a", 10d);

    // check if insert was successful
    Assertions.assertEquals("a", sequence.getOutcomes().get(0));
    Assertions.assertEquals(10d, sequence.getProbs()[0]);
  }

  /**
   * Tests {@link Sequence#compareTo(Sequence)}.
   */
  @Test
  void testCompareTo() {
    Sequence lowScore = new Sequence();
    lowScore.add("A", 1d);
    lowScore.add("B", 2d);
    lowScore.add("C", 3d);

    Sequence highScore = new Sequence();
    lowScore.add("A", 7d);
    lowScore.add("B", 8d);
    lowScore.add("C", 9d);

    Assertions.assertEquals(-1, lowScore.compareTo(highScore));
    Assertions.assertEquals(1, highScore.compareTo(lowScore));
  }

  /**
   * Checks that {@link Sequence#toString()} is executable.
   */
  @Test
  void testToString() {
    new Sequence().toString();

    Sequence sequence = new Sequence();
    sequence.add("test", 0.1d);
    sequence.toString();
  }

  @Test
  void testGetAtIndex() {
    final Sequence sequence = new Sequence();
    sequence.add("A", 1d);
    sequence.add("B", 2d);
    sequence.add("C", 3d);

    Assertions.assertEquals(3, sequence.getSize());

    Assertions.assertEquals("A", sequence.getOutcome(0));
    Assertions.assertEquals("B", sequence.getOutcome(1));
    Assertions.assertEquals("C", sequence.getOutcome(2));

    Assertions.assertEquals(1d, sequence.getProb(0));
    Assertions.assertEquals(2d, sequence.getProb(1));
    Assertions.assertEquals(3d, sequence.getProb(2));
  }

  @Test
  void testGetAtIndexInvalid() {
    final Sequence sequence = new Sequence();
    sequence.add("A", 1d);

    Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> sequence.getOutcome(-1));
    Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> sequence.getOutcome(sequence.getSize() + 1));
    Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> sequence.getProb(-1));
    Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> sequence.getProb(sequence.getSize() + 1));
  }

  @Test
  void testListCopy() {
    final Sequence sequence = new Sequence();
    sequence.add("A", 1d);

    Assertions.assertEquals(1, sequence.getSize());
    Assertions.assertThrows(UnsupportedOperationException.class, () -> sequence.getOutcomes().add(
                    "This should fail! It should not be possible to modify the internal state"));
  }
}
