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
package opennlp.tools.util.normalizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfusableSkeletonTest {

  private static String cp(int codePoint) {
    return new String(Character.toChars(codePoint));
  }

  @Test
  void testSkeletonFoldsKnownLookalikesToTheirLatinPrototype() {
    // Known single-character mappings from the bundled UTS #39 table.
    assertEquals(Confusables.skeleton("a"), Confusables.skeleton(cp(0x0430))); // Cyrillic a -> a
    assertEquals(Confusables.skeleton("o"), Confusables.skeleton(cp(0x03BF))); // Greek omicron -> o

    // A whole word spelled with Cyrillic look-alikes reduces to the same skeleton as its Latin twin.
    final String latin = "paypal";
    final String spoofed = "p" + cp(0x0430) + "yp" + cp(0x0430) + "l";
    assertEquals(Confusables.skeleton(latin), Confusables.skeleton(spoofed));
  }

  @Test
  void testConfusableDetectsLookalikesAndSeparatesDistinctText() {
    final String spoofed = "p" + cp(0x0430) + "yp" + cp(0x0430) + "l";
    assertTrue(Confusables.confusable("paypal", spoofed));
    assertTrue(Confusables.confusable("paypal", "paypal")); // reflexive identity
    assertFalse(Confusables.confusable("cat", "dog"));
  }

  @Test
  void testAsciiTextSkeletonsToItself() {
    // Plain ASCII letters are prototype targets, not confusable sources, so the skeleton is the
    // text unchanged.
    assertEquals("abracadabra", Confusables.skeleton("abracadabra"));
  }
}
