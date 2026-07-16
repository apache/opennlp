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

package opennlp.tools.depparse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the outcome encoding of {@link Transition}: every transition must render to a
 * unique outcome string and decode back to an equal transition, and every malformed
 * outcome must be rejected loudly.
 */
public class TransitionTest {

  @Test
  void testEncodeRendersTheOutcomeStrings() {
    assertEquals("SHIFT", Transition.SHIFT.encode());
    assertEquals("LEFT_ARC:nsubj", Transition.leftArc("nsubj").encode());
    assertEquals("RIGHT_ARC:obj", Transition.rightArc("obj").encode());
  }

  @Test
  void testDecodeRestoresEncodedTransitions() {
    assertSame(Transition.SHIFT, Transition.decode("SHIFT"));
    assertEquals(Transition.leftArc("nsubj"), Transition.decode("LEFT_ARC:nsubj"));
    assertEquals(Transition.rightArc("obj"), Transition.decode("RIGHT_ARC:obj"));
  }

  @Test
  void testLabelContainingTheSeparatorRoundTrips() {
    // Only the first separator splits type from label, so a label containing the
    // separator character itself survives the round trip unchanged.
    final Transition transition = Transition.leftArc("nmod:poss");
    assertEquals("LEFT_ARC:nmod:poss", transition.encode());
    assertEquals(transition, Transition.decode(transition.encode()));
  }

  @Test
  void testDecodeRejectsMalformedOutcomes() {
    assertThrows(IllegalArgumentException.class, () -> Transition.decode(null));
    assertThrows(IllegalArgumentException.class, () -> Transition.decode("UNKNOWN"));
    assertThrows(IllegalArgumentException.class, () -> Transition.decode("UNKNOWN:det"));
    // A labeled shift is contradictory and must be rejected by the record invariant.
    assertThrows(IllegalArgumentException.class, () -> Transition.decode("SHIFT:det"));
  }

  @Test
  void testConstructorValidation() {
    assertThrows(IllegalArgumentException.class, () -> new Transition(null, "det"));
    assertThrows(IllegalArgumentException.class,
        () -> new Transition(Transition.Type.SHIFT, "det"));
    assertThrows(IllegalArgumentException.class, () -> Transition.leftArc(null));
    assertThrows(IllegalArgumentException.class, () -> Transition.rightArc(" "));
  }
}
