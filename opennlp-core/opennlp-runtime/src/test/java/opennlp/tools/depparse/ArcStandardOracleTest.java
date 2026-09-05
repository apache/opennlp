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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link ArcStandardOracle} derivations replay to the gold graph through
 * {@link ArcStandardState}, and that non-projective input is rejected.
 */
public class ArcStandardOracleTest {

  /**
   * Derives the oracle transitions for {@code gold} and replays them on a fresh state.
   *
   * @param gold The gold graph to derive from.
   * @return The graph the replayed derivation builds. Never {@code null}.
   */
  private static DependencyGraph replay(DependencyGraph gold) {
    final List<Transition> transitions = ArcStandardOracle.transitions(gold);
    // every token is shifted once and attached once
    assertEquals(2 * gold.size(), transitions.size());
    final ArcStandardState state = new ArcStandardState(gold.size());
    for (final Transition transition : transitions) {
      assertTrue(state.canApply(transition));
      state.apply(transition);
    }
    return state.toGraph();
  }

  @Test
  void testRoundTripSimpleSentence() {
    final DependencyGraph gold = DependencyGraph.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"});
    assertEquals(gold, replay(gold));
  }

  @Test
  void testRoundTripSingleToken() {
    final DependencyGraph gold = DependencyGraph.of(new int[] {-1}, new String[] {"root"});
    assertEquals(gold, replay(gold));
  }

  @Test
  void testRoundTripRightBranching() {
    // "eat fresh fish now": root with a right dependent that has its own left dependent
    final DependencyGraph gold = DependencyGraph.of(new int[] {-1, 2, 0, 0},
        new String[] {"root", "amod", "obj", "advmod"});
    assertEquals(gold, replay(gold));
  }

  @Test
  void testRoundTripDeepChain() {
    final DependencyGraph gold = DependencyGraph.of(new int[] {1, 2, 3, -1},
        new String[] {"a", "b", "c", "root"});
    assertEquals(gold, replay(gold));
  }

  @Test
  void testNonProjectiveThrows() {
    // arcs (2,0) and (3,1) cross, so there is no arc-standard derivation
    final DependencyGraph nonProjective = DependencyGraph.of(new int[] {2, 3, -1, 2},
        new String[] {"a", "b", "root", "c"});
    assertThrows(IllegalArgumentException.class,
        () -> ArcStandardOracle.transitions(nonProjective));
  }

  @Test
  void testAllTreesThroughFiveTokens() {
    for (int size = 1; size <= 5; size++) {
      final int[] heads = new int[size];
      checkHeadAssignments(heads, 0);
    }
  }

  /**
   * Enumerates every head assignment and checks each valid tree against the
   * arc-crossing definition of projectivity.
   */
  private static void checkHeadAssignments(int[] heads, int index) {
    if (index < heads.length) {
      for (int head = DependencyArc.ROOT_HEAD; head < heads.length; head++) {
        heads[index] = head;
        checkHeadAssignments(heads, index + 1);
      }
      return;
    }

    final String[] relations = new String[heads.length];
    for (int i = 0; i < relations.length; i++) {
      relations[i] = heads[i] == DependencyArc.ROOT_HEAD ? "root" : "dep";
    }
    final DependencyGraph graph;
    try {
      graph = DependencyGraph.of(heads, relations);
    } catch (IllegalArgumentException e) {
      return;
    }

    if (isProjective(graph)) {
      assertEquals(graph, replay(graph), graph.toString());
    } else {
      assertThrows(IllegalArgumentException.class,
          () -> ArcStandardOracle.transitions(graph), graph.toString());
    }
  }

  /** Returns whether no pair of arcs crosses in token order. */
  private static boolean isProjective(DependencyGraph graph) {
    for (int first = 0; first < graph.size(); first++) {
      final int firstLow = Math.min(first, graph.headOf(first));
      final int firstHigh = Math.max(first, graph.headOf(first));
      for (int second = first + 1; second < graph.size(); second++) {
        final int secondLow = Math.min(second, graph.headOf(second));
        final int secondHigh = Math.max(second, graph.headOf(second));
        if (firstLow < secondLow && secondLow < firstHigh && firstHigh < secondHigh
            || secondLow < firstLow && firstLow < secondHigh && secondHigh < firstHigh) {
          return false;
        }
      }
    }
    return true;
  }

  @Test
  void testNullGraphThrows() {
    assertThrows(IllegalArgumentException.class, () -> ArcStandardOracle.transitions(null));
  }
}
