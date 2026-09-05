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
 * The feature template of the feedforward parser: a fixed set of configuration
 * positions whose words, tags, and arc labels are embedded and concatenated into the
 * network input, following
 * <a href="https://aclanthology.org/D14-1082/">Chen and Manning (2014)</a>.
 *
 * <p>Positions: the top three stack and buffer items; the leftmost and rightmost
 * dependents of the top two stack items; and the leftmost dependent of the leftmost
 * dependent and rightmost dependent of the rightmost dependent of the top two stack
 * items, capturing second-order structure. Words and tags are read for all positions,
 * labels only for the dependent positions, whose relations are already assigned.</p>
 */
final class FeedforwardContext {

  /** The number of positions whose word and tag are embedded. */
  static final int POSITIONS = 14;

  /** The number of dependent positions whose arc label is embedded. */
  static final int LABEL_POSITIONS = 8;

  /** The number of symbolic features in one parser configuration. */
  static final int FEATURE_COUNT = 2 * POSITIONS + LABEL_POSITIONS;

  /** The index of the first dependent position; the stack and buffer items precede it. */
  private static final int FIRST_DEPENDENT_POSITION = 6;

  /** Prevents construction of this utility class. */
  private FeedforwardContext() {
  }

  /**
   * Extracts the symbolic features of a configuration: {@link #POSITIONS} words, then
   * {@link #POSITIONS} tags, then {@link #LABEL_POSITIONS} labels; absent positions
   * yield {@code null} entries, which the vocabulary maps to its padding symbol.
   *
   * @param state The configuration to describe. Must not be {@code null}.
   * @param tokens The sentence tokens. Must not be {@code null}.
   * @param tags The part-of-speech tags aligned with {@code tokens}. Must not be
   *             {@code null}.
   * @return The symbolic features, in the order described above. Never {@code null}.
   */
  static String[] extract(ArcStandardState state, String[] tokens, String[] tags) {
    final int s0 = state.stack(0);
    final int s1 = state.stack(1);
    final int[] positions = {
        s0, s1, state.stack(2),
        state.buffer(0), state.buffer(1), state.buffer(2),
        leftmost(state, s0), rightmost(state, s0),
        leftmost(state, s1), rightmost(state, s1),
        leftmost(state, leftmost(state, s0)), rightmost(state, rightmost(state, s0)),
        leftmost(state, leftmost(state, s1)), rightmost(state, rightmost(state, s1))
    };
    final String[] features = new String[FEATURE_COUNT];
    for (int i = 0; i < POSITIONS; i++) {
      features[i] = symbol(tokens, positions[i]);
      features[POSITIONS + i] = symbol(tags, positions[i]);
    }
    for (int i = 0; i < LABEL_POSITIONS; i++) {
      final int position = positions[FIRST_DEPENDENT_POSITION + i];
      features[2 * POSITIONS + i] =
          position >= 0 ? state.assignedRelation(position) : null;
    }
    return features;
  }

  /** The leftmost dependent of a position, or the absence marker for absent positions. */
  private static int leftmost(ArcStandardState state, int index) {
    return index >= 0 ? state.leftmostDependent(index) : ArcStandardState.NONE;
  }

  /** The rightmost dependent of a position, or the absence marker for absent positions. */
  private static int rightmost(ArcStandardState state, int index) {
    return index >= 0 ? state.rightmostDependent(index) : ArcStandardState.NONE;
  }

  /** The value at a position: the root symbol for the root, {@code null} when absent. */
  private static String symbol(String[] values, int index) {
    if (index == ArcStandardState.ROOT) {
      return FeedforwardDependencyModel.ROOT_SYMBOL;
    }
    return index == ArcStandardState.NONE ? null : values[index];
  }
}
