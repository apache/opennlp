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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the configuration mechanics of {@link ArcStandardState}: the start
 * configuration, transition applicability at the boundaries, the bookkeeping of attached
 * dependents, copy independence, and the fail-loud behavior of every accessor.
 */
public class ArcStandardStateTest {

  @Test
  void testInitialConfiguration() {
    final ArcStandardState state = new ArcStandardState(3);
    assertEquals(ArcStandardState.ROOT, state.stack(0));
    assertEquals(ArcStandardState.NONE, state.stack(1));
    assertEquals(0, state.buffer(0));
    assertEquals(1, state.buffer(1));
    assertEquals(2, state.buffer(2));
    assertEquals(ArcStandardState.NONE, state.buffer(3));
    assertEquals(1, state.stackSize());
    assertEquals(3, state.bufferSize());
    assertFalse(state.isTerminal());
  }

  @Test
  void testSingleTokenDerivationIsForced() {
    // With one token the system permits exactly one derivation: shift the token, then
    // attach it to the artificial root with a right arc.
    final ArcStandardState state = new ArcStandardState(1);
    assertTrue(state.canApply(Transition.SHIFT));
    assertFalse(state.canApply(Transition.leftArc("det")));
    assertFalse(state.canApply(Transition.rightArc("root")));

    state.apply(Transition.SHIFT);
    assertFalse(state.canApply(Transition.SHIFT));
    assertFalse(state.canApply(Transition.leftArc("det")));
    assertTrue(state.canApply(Transition.rightArc("root")));

    state.apply(Transition.rightArc("root"));
    assertTrue(state.isTerminal());
    assertEquals(DependencyGraph.of(new int[] {-1}, new String[] {"root"}),
        state.toGraph());
  }

  @Test
  void testDependentBookkeepingDuringADerivation() {
    // Derives "the dog barks" (the<-dog via det, dog<-barks via nsubj, barks<-root) and
    // checks the partial-structure accessors after every attachment.
    final ArcStandardState state = new ArcStandardState(3);
    state.apply(Transition.SHIFT);
    state.apply(Transition.SHIFT);
    assertEquals(0, state.assignedDependents(1));
    assertNull(state.assignedRelation(0));

    state.apply(Transition.leftArc("det"));
    assertEquals(1, state.assignedDependents(1));
    assertEquals(0, state.leftmostDependent(1));
    assertEquals(0, state.rightmostDependent(1));
    assertEquals("det", state.assignedRelation(0));

    state.apply(Transition.SHIFT);
    state.apply(Transition.leftArc("nsubj"));
    assertEquals(1, state.leftmostDependent(2));
    assertEquals("nsubj", state.assignedRelation(1));

    state.apply(Transition.rightArc("root"));
    assertTrue(state.isTerminal());
    assertEquals(DependencyGraph.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"}), state.toGraph());
  }

  @Test
  void testInapplicableTransitionFailsLoud() {
    final ArcStandardState state = new ArcStandardState(2);
    assertThrows(IllegalArgumentException.class,
        () -> state.apply(Transition.leftArc("det")));
    assertThrows(IllegalArgumentException.class,
        () -> state.apply(Transition.rightArc("root")));
    assertThrows(IllegalArgumentException.class, () -> state.apply(null));
    assertThrows(IllegalArgumentException.class, () -> state.canApply(null));
  }

  @Test
  void testToGraphBeforeTerminalFailsLoud() {
    final ArcStandardState state = new ArcStandardState(2);
    assertThrows(IllegalStateException.class, state::toGraph);
    state.apply(Transition.SHIFT);
    assertThrows(IllegalStateException.class, state::toGraph);
  }

  @Test
  void testCopyIsIndependentOfTheOriginal() {
    final ArcStandardState original = new ArcStandardState(2);
    original.apply(Transition.SHIFT);
    final ArcStandardState copy = original.copy();

    copy.apply(Transition.SHIFT);
    copy.apply(Transition.leftArc("nsubj"));
    // The copy advanced by two transitions while the original still has one token
    // buffered and one on the stack.
    assertEquals(2, original.stackSize());
    assertEquals(1, original.bufferSize());
    assertEquals(0, original.assignedDependents(1));
    assertEquals(1, copy.assignedDependents(1));
  }

  @Test
  void testAccessorValidation() {
    final ArcStandardState state = new ArcStandardState(2);
    assertThrows(IllegalArgumentException.class, () -> state.stack(-1));
    assertThrows(IllegalArgumentException.class, () -> state.buffer(-1));
    assertThrows(IllegalArgumentException.class, () -> state.assignedDependents(-1));
    assertThrows(IllegalArgumentException.class, () -> state.assignedDependents(2));
    assertThrows(IllegalArgumentException.class, () -> state.leftmostDependent(2));
    assertThrows(IllegalArgumentException.class, () -> state.rightmostDependent(-1));
    assertThrows(IllegalArgumentException.class, () -> state.assignedRelation(2));
  }

  @Test
  void testTokenCountMustBePositive() {
    assertThrows(IllegalArgumentException.class, () -> new ArcStandardState(0));
    assertThrows(IllegalArgumentException.class, () -> new ArcStandardState(-1));
  }
}
