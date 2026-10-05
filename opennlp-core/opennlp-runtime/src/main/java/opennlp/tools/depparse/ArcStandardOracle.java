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

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.ArgumentChecks;

/**
 * Turns a known correct dependency tree into the sequence of arc-standard parser actions
 * that rebuilds it. Those actions are the answers the parser is trained on.
 *
 * <p>The parser keeps a stack of tokens being processed and a buffer of tokens not read
 * yet. For each step the oracle picks one of three actions, using the annotated tree to
 * decide the head and the relation label:</p>
 * <ul>
 * <li>{@code SHIFT}: move the next token from the buffer onto the stack.</li>
 * <li>{@code LEFT_ARC(label)}: make the top token the head of the token below it, and
 * remove that dependent from the stack.</li>
 * <li>{@code RIGHT_ARC(label)}: make the second token the head of the top token, and
 * remove that dependent from the stack.</li>
 * </ul>
 *
 * <p>An arc is only created once all dependents of the token being attached have been
 * collected. The oracle is defined for projective trees only; a non-projective tree has
 * no arc-standard derivation and is rejected.</p>
 *
 * @since 3.0.0
 */
final class ArcStandardOracle {

  /** Prevents construction of this utility class. */
  private ArcStandardOracle() {
  }

  /**
   * Derives the gold transition sequence for a tree.
   *
   * @param gold The gold dependency tree. Must not be {@code null} and must be
   *             projective.
   * @return The transitions that rebuild {@code gold} from the start configuration, in
   *         order. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code gold} is {@code null} or not
   *         projective.
   */
  static List<Transition> transitions(DependencyTree gold) {
    ArgumentChecks.requireNonNullArg(gold, "gold");
    final int n = gold.size();
    final int[] goldDependents = new int[n];
    for (int i = 0; i < n; i++) {
      final int head = gold.headOf(i);
      if (head >= 0) {
        goldDependents[head]++;
      }
    }

    final ArcStandardState state = new ArcStandardState(n);
    final List<Transition> transitions = new ArrayList<>(2 * n);
    while (!state.isTerminal()) {
      final Transition next = nextTransition(gold, goldDependents, state);
      if (next == null) {
        throw new IllegalArgumentException(
            "gold tree has no arc-standard derivation (non-projective): " + gold);
      }
      state.apply(next);
      transitions.add(next);
    }
    return transitions;
  }

  /**
   * Picks the gold transition for the current configuration.
   *
   * @param gold The gold tree being derived.
   * @param goldDependents The gold dependent count per token, indexed by head.
   * @param state The current configuration.
   * @return The next gold transition, or {@code null} when the configuration is stuck,
   *         which only happens for non-projective input.
   */
  private static Transition nextTransition(DependencyTree gold, int[] goldDependents,
      ArcStandardState state) {
    final int s0 = state.stack(0);
    final int s1 = state.stack(1);
    if (s1 >= 0 && gold.headOf(s1) == s0) {
      final Transition leftArc = Transition.leftArc(gold.relationOf(s1));
      if (state.canApply(leftArc)) {
        return leftArc;
      }
    }
    if (s0 >= 0 && s1 != ArcStandardState.NONE && gold.headOf(s0) == s1
        && state.assignedDependents(s0) == goldDependents[s0]) {
      final Transition rightArc = Transition.rightArc(gold.relationOf(s0));
      if (state.canApply(rightArc)) {
        return rightArc;
      }
    }
    if (state.canApply(Transition.SHIFT)) {
      return Transition.SHIFT;
    }
    return null;
  }

  /**
   * Tests whether a gold tree is projective: no pair of arcs crosses when the arcs are
   * placed above the token sequence. The projective trees are the ones with an
   * arc-standard derivation, so callers apply this test before requesting
   * {@link #transitions}.
   *
   * @param gold The gold tree. Must not be {@code null}.
   * @return {@code true} if no pair of arcs crosses.
   * @throws IllegalArgumentException Thrown if {@code gold} is {@code null}.
   */
  static boolean isProjective(DependencyTree gold) {
    ArgumentChecks.requireNonNullArg(gold, "gold");
    for (int first = 0; first < gold.size(); first++) {
      final int firstLow = Math.min(first, gold.headOf(first));
      final int firstHigh = Math.max(first, gold.headOf(first));
      for (int other = first + 1; other < gold.size(); other++) {
        final int otherLow = Math.min(other, gold.headOf(other));
        final int otherHigh = Math.max(other, gold.headOf(other));
        if ((firstLow < otherLow && otherLow < firstHigh && firstHigh < otherHigh)
            || (otherLow < firstLow && firstLow < otherHigh && otherHigh < firstHigh)) {
          return false;
        }
      }
    }
    return true;
  }
}
