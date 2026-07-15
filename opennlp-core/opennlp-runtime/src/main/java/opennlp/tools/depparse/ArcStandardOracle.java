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


/**
 * The static oracle for the arc-standard system: derives the transition sequence that
 * reproduces a gold {@link DependencyGraph}.
 *
 * <p>An arc is only created once all dependents of the token being attached have been
 * collected, which is the arc-standard correctness condition. The oracle is defined for
 * projective trees only; a non-projective gold graph has no arc-standard derivation and
 * is rejected.</p>
 *
 * @since 3.0.0
 */
public final class ArcStandardOracle {

  private ArcStandardOracle() {
    // static oracle, not meant to be instantiated
  }

  /**
   * Derives the gold transition sequence for a graph.
   *
   * @param gold The gold dependency graph. Must not be {@code null} and must be
   *             projective.
   * @return The transitions that rebuild {@code gold} from the start configuration, in
   *         order. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code gold} is {@code null} or not
   *         projective.
   */
  public static List<Transition> transitions(DependencyGraph gold) {
    if (gold == null) {
      throw new IllegalArgumentException("gold must not be null");
    }
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
            "gold graph has no arc-standard derivation (non-projective): " + gold);
      }
      state.apply(next);
      transitions.add(next);
    }
    return transitions;
  }

  /**
   * Picks the gold transition for the current configuration, or {@code null} when the
   * configuration is stuck, which only happens for non-projective input.
   */
  private static Transition nextTransition(DependencyGraph gold, int[] goldDependents,
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
}
