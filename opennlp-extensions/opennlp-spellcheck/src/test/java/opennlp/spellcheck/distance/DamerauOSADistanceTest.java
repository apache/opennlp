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

package opennlp.spellcheck.distance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DamerauOSADistanceTest {

  private final EditDistance d = DamerauOSADistance.INSTANCE;

  @Test
  void equalStringsHaveZeroDistance() {
    assertEquals(0, d.distance("kitten", "kitten", 2));
  }

  @Test
  void singleSubstitution() {
    assertEquals(1, d.distance("cat", "car", 2));
  }

  @Test
  void transpositionCostsOne() {
    // "ca" -> "ac" is a single adjacent transposition in OSA.
    assertEquals(1, d.distance("ca", "ac", 2));
    assertEquals(1, d.distance("converse", "covnerse", 2));
  }

  @Test
  void classicLevenshteinExamples() {
    assertEquals(3, d.distance("kitten", "sitting", 5));
    assertEquals(2, d.distance("flaw", "lawn", 5));
  }

  @Test
  void aboveMaxReturnsMinusOne() {
    assertEquals(-1, d.distance("kitten", "sitting", 2));
    assertEquals(-1, d.distance("abc", "xyz", 2));
  }

  @Test
  void emptyStrings() {
    assertEquals(0, d.distance("", "", 2));
    assertEquals(3, d.distance("", "abc", 5));
    assertEquals(3, d.distance("abc", "", 5));
  }

  @Test
  void unicodeCodePointsCountAsOneSymbol() {
    // Two distinct supplementary characters differ by exactly one substitution.
    final String a = new String(Character.toChars(0x1F600)); // grinning face
    final String b = new String(Character.toChars(0x1F601)); // grinning face with eyes
    assertEquals(1, d.distance(a, b, 2));
  }
}
