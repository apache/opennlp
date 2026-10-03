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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

  /** Checks the projectivity test on a graph with crossed arcs, a chain, and a null. */
  @Test
  void testIsProjective() {
    assertFalse(ArcStandardOracle.isProjective(DependencyGraph.of(new int[] {2, 3, -1, 2},
        new String[] {"a", "b", "root", "c"})));
    assertTrue(ArcStandardOracle.isProjective(DependencyGraph.of(new int[] {1, -1, 1},
        new String[] {"nsubj", "root", "obj"})));
    assertThrows(IllegalArgumentException.class, () -> ArcStandardOracle.isProjective(null));
  }

  @Test
  void testNonProjectiveThrows() {
    // arcs (2,0) and (3,1) cross, so there is no arc-standard derivation
    final DependencyGraph nonProjective = DependencyGraph.of(new int[] {2, 3, -1, 2},
        new String[] {"a", "b", "root", "c"});
    assertThrows(IllegalArgumentException.class,
        () -> ArcStandardOracle.transitions(nonProjective));
  }

  @ParameterizedTest(name = "all trees with {0} token(s)")
  @ValueSource(ints = {1, 2, 3, 4, 5})
  void testAllTreesOfSize(int size) {
    checkHeadAssignments(new int[size], 0);
  }

  /**
   * Enumerates every head assignment and checks each valid tree against the
   * contiguous-subtree definition of projectivity.
   *
   * @param heads The head assignment under construction; positions before {@code index}
   *              are fixed.
   * @param index The next position to assign a head to.
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

    assertEquals(isProjective(graph), ArcStandardOracle.isProjective(graph), graph.toString());
    if (isProjective(graph)) {
      assertEquals(graph, replay(graph), graph.toString());
    } else {
      assertThrows(IllegalArgumentException.class,
          () -> ArcStandardOracle.transitions(graph), graph.toString());
    }
  }

  /**
   * Decides projectivity by an independent definition: every token's subtree covers a
   * contiguous span of the sentence. This agrees with the arc-crossing test that
   * {@link ArcStandardOracle#isProjective} implements because the artificial root sits
   * to the left of the sentence.
   *
   * @param graph The graph to inspect.
   * @return {@code true} if every subtree is a contiguous span.
   */
  private static boolean isProjective(DependencyGraph graph) {
    for (int token = 0; token < graph.size(); token++) {
      int first = token;
      int last = token;
      int covered = 0;
      for (int other = 0; other < graph.size(); other++) {
        if (dominates(graph, token, other)) {
          first = Math.min(first, other);
          last = Math.max(last, other);
          covered++;
        }
      }
      if (last - first + 1 != covered) {
        return false;
      }
    }
    return true;
  }

  /**
   * Tests whether a token is another token or one of its ancestors.
   *
   * @param graph The graph to inspect.
   * @param ancestor The candidate ancestor.
   * @param token The token whose head chain is followed.
   * @return {@code true} if {@code ancestor} is on the head chain from {@code token}.
   */
  private static boolean dominates(DependencyGraph graph, int ancestor, int token) {
    for (int current = token; current != DependencyArc.ROOT_HEAD;
        current = graph.headOf(current)) {
      if (current == ancestor) {
        return true;
      }
    }
    return false;
  }

  @Test
  void testNullGraphThrows() {
    assertThrows(IllegalArgumentException.class, () -> ArcStandardOracle.transitions(null));
  }
}
