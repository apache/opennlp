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
 * One step of arc-standard beam search over a {@link FeedforwardNetwork}, shared by
 * beam decoding and beam refinement: every node of the beam is expanded by each
 * applicable transition scored by summed log-probability, and the best expansions
 * are advanced.
 *
 * @since 3.0.0
 */
final class ArcStandardBeam {

  /** The transition index of the start node, which took no transition. */
  static final int NO_TRANSITION = -1;

  /** Prevents construction of this utility class. */
  private ArcStandardBeam() {
  }

  /**
   * One path of the beam. The parent link forms the history; the start node has no
   * parent and no features.
   */
  static final class Node {
    final Node parent;
    final int[] features;
    final int transition;
    final double score;
    final boolean gold;

    /** The configuration after {@link #transition}; set by {@link #advance}. */
    ArcStandardState state;

    /** The candidate weight summed over the paths through this node by the trainer. */
    double weight;

    /** Whether the trainer has already collected this node for an update. */
    boolean collected;

    /**
     * Creates the start node of a sentence.
     *
     * @param state The initial configuration.
     */
    Node(ArcStandardState state) {
      this(null, null, NO_TRANSITION, 0.0, true);
      this.state = state;
    }

    /**
     * Extends {@code parent} by one transition.
     *
     * @param parent The node this one extends.
     * @param features The embedding rows the transition was scored on.
     * @param transition The transition index taken from {@code parent}.
     * @param score The summed log-probability of the path.
     * @param gold Whether the path is the gold derivation so far.
     */
    Node(Node parent, int[] features, int transition, double score, boolean gold) {
      this.parent = parent;
      this.features = features;
      this.transition = transition;
      this.score = score;
      this.gold = gold;
    }
  }

  /**
   * Expands every node of the beam by its applicable transitions. Terminal nodes pass
   * through unchanged.
   *
   * @param beam The nodes to expand, each with its state set.
   * @param context The sentence features.
   * @param network The network scoring the configurations.
   * @param cache The contribution cache to score with, or {@code null}.
   * @param transitions The decoded transition inventory.
   * @param goldTransition The transition index that continues the gold derivation, or
   *                       {@link #NO_TRANSITION} when the gold path is not tracked.
   * @param hidden Scratch space for the hidden layer of the network; overwritten.
   * @param scores Scratch space for the transition scores of the network; overwritten.
   * @return The expansions, sorted by descending score, with their states unset.
   *         Never {@code null}.
   * @throws IllegalStateException Thrown if the network produces a non-finite score.
   */
  static List<Node> expand(List<Node> beam, FeedforwardContext context,
      FeedforwardNetwork network, ContributionCache cache, Transition[] transitions,
      int goldTransition, double[] hidden, double[] scores) {
    final List<Node> expansions = new ArrayList<>();
    for (final Node node : beam) {
      if (node.state.isTerminal()) {
        expansions.add(node);
        continue;
      }
      final int[] features = context.featureIds(node.state);
      final double[] logProbabilities = network.score(features, cache, hidden, scores);
      FeedforwardNetwork.logSoftmaxInPlace(logProbabilities);
      for (int i = 0; i < transitions.length; i++) {
        if (node.state.canApply(transitions[i])) {
          expansions.add(new Node(node, features, i, node.score + logProbabilities[i],
              node.gold && i == goldTransition));
        }
      }
    }
    expansions.sort((a, b) -> Double.compare(b.score, a.score));
    return expansions;
  }

  /**
   * Keeps the best expansions and advances their states.
   *
   * @param expansions The expansions in descending score order.
   * @param beamSize The number of nodes to keep.
   * @param transitions The decoded transition inventory.
   * @return The survivors, each with its state set. Never {@code null}.
   */
  static List<Node> advance(List<Node> expansions, int beamSize, Transition[] transitions) {
    final int survivorCount = Math.min(beamSize, expansions.size());
    final List<Node> survivors = new ArrayList<>(survivorCount);
    for (int i = 0; i < survivorCount; i++) {
      final Node survivor = expansions.get(i);
      if (survivor.state == null) {
        survivor.state = survivor.parent.state.copy();
        survivor.state.apply(transitions[survivor.transition]);
      }
      survivors.add(survivor);
    }
    return survivors;
  }
}
