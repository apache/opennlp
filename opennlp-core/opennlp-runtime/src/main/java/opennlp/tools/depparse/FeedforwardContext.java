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

/**
 * The feature template of the feedforward parser for one sentence: a fixed set of
 * configuration positions whose words, tags, and arc labels are embedded and
 * concatenated into the network input, following
 * <a href="https://aclanthology.org/D14-1082/">Chen and Manning (2014)</a>.
 *
 * <p>Positions: the top three stack and buffer items; the leftmost and rightmost
 * dependents of the top two stack items; and the leftmost dependent of the leftmost
 * dependent and rightmost dependent of the rightmost dependent of the top two stack
 * items, capturing second-order structure. Words and tags are read for all positions,
 * labels only for the dependent positions, whose relations are already assigned.</p>
 *
 * <p>The tokens and tags are resolved to embedding rows once per instance, so
 * extracting the features of a configuration only indexes. An instance holds scratch
 * state for that purpose and is used by one thread for one sentence.</p>
 *
 * @since 3.0.0
 */
final class FeedforwardContext {

  /** The number of positions whose word and tag are embedded. */
  static final int POSITIONS = 14;

  /** The number of dependent positions whose arc label is embedded. */
  static final int LABEL_POSITIONS = 8;

  /** The number of features in one parser configuration. */
  static final int FEATURE_COUNT = 2 * POSITIONS + LABEL_POSITIONS;

  /** The index of the first dependent position; the stack and buffer items precede it. */
  private static final int FIRST_DEPENDENT_POSITION = 6;

  private final FeedforwardNetwork network;
  private final int[] wordRows;
  private final int[] tagRows;

  /** Scratch for the token index of every position. */
  private final int[] positions = new int[POSITIONS];

  /**
   * Resolves a sentence against the network vocabularies.
   *
   * @param network The network whose rows the features index.
   * @param tokens The sentence tokens. Must not contain {@code null}.
   * @param tags The part-of-speech tags aligned with {@code tokens}. Must not contain
   *             {@code null}.
   */
  FeedforwardContext(FeedforwardNetwork network, String[] tokens, String[] tags) {
    this.network = network;
    this.wordRows = network.wordRows(tokens);
    this.tagRows = network.tagRows(tags);
  }

  /**
   * Extracts the embedding rows of a configuration into a new array.
   *
   * @param state The configuration to describe.
   * @return {@link #POSITIONS} word rows, then {@link #POSITIONS} tag rows, then
   *         {@link #LABEL_POSITIONS} label rows. Never {@code null}.
   */
  int[] featureIds(ArcStandardState state) {
    final int[] features = new int[FEATURE_COUNT];
    featureIds(state, features);
    return features;
  }

  /**
   * Extracts the embedding rows of a configuration into {@code features}: absent
   * positions take the absent-position row of their vocabulary and the artificial
   * root takes the root row.
   *
   * @param state The configuration to describe.
   * @param features Receives the rows in the order of {@link #featureIds(ArcStandardState)};
   *                 overwritten. Must have {@link #FEATURE_COUNT} entries.
   */
  void featureIds(ArcStandardState state, int[] features) {
    final int s0 = state.stack(0);
    final int s1 = state.stack(1);
    positions[0] = s0;
    positions[1] = s1;
    positions[2] = state.stack(2);
    positions[3] = state.buffer(0);
    positions[4] = state.buffer(1);
    positions[5] = state.buffer(2);
    positions[6] = leftmost(state, s0);
    positions[7] = rightmost(state, s0);
    positions[8] = leftmost(state, s1);
    positions[9] = rightmost(state, s1);
    positions[10] = leftmost(state, positions[6]);
    positions[11] = rightmost(state, positions[7]);
    positions[12] = leftmost(state, positions[8]);
    positions[13] = rightmost(state, positions[9]);
    for (int i = 0; i < POSITIONS; i++) {
      final int position = positions[i];
      if (position == ArcStandardState.ROOT) {
        features[i] = network.rootWordRow();
        features[POSITIONS + i] = network.rootTagRow();
      } else if (position == ArcStandardState.NONE) {
        features[i] = network.noneWordRow();
        features[POSITIONS + i] = network.noneTagRow();
      } else {
        features[i] = wordRows[position];
        features[POSITIONS + i] = tagRows[position];
      }
    }
    for (int i = 0; i < LABEL_POSITIONS; i++) {
      final int position = positions[FIRST_DEPENDENT_POSITION + i];
      features[2 * POSITIONS + i] =
          network.labelRow(position >= 0 ? state.assignedRelation(position) : null);
    }
  }

  /**
   * @param state The configuration.
   * @param index The token index of a position, or {@link ArcStandardState#NONE}.
   * @return The leftmost dependent of the position, or {@link ArcStandardState#NONE}
   *         if the position is absent or has no dependent.
   */
  private static int leftmost(ArcStandardState state, int index) {
    return index >= 0 ? state.leftmostDependent(index) : ArcStandardState.NONE;
  }

  /**
   * @param state The configuration.
   * @param index The token index of a position, or {@link ArcStandardState#NONE}.
   * @return The rightmost dependent of the position, or {@link ArcStandardState#NONE}
   *         if the position is absent or has no dependent.
   */
  private static int rightmost(ArcStandardState state, int index) {
    return index >= 0 ? state.rightmostDependent(index) : ArcStandardState.NONE;
  }
}
