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

import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TAGS;
import static opennlp.tools.depparse.DependencyTestSamples.THE_DOG_BARKS_TOKENS;
import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static opennlp.tools.depparse.DependencyTestSamples.feedforwardParameters;
import static opennlp.tools.depparse.DependencyTestSamples.trainFeedforward;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the slot order of {@link FeedforwardContext#featureIds(ArcStandardState)}: the
 * word rows of the {@value FeedforwardContext#POSITIONS} positions, then their tag
 * rows, then the label rows of the dependent positions.
 */
public class FeedforwardContextTest {

  private static FeedforwardNetwork network;
  private static int[] wordRows;
  private static int[] tagRows;

  /**
   * Trains a small network whose vocabulary covers the sentence under test.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @BeforeAll
  static void trainNetwork() throws IOException {
    network = trainFeedforward(corpus(), feedforwardParameters(1, 0.01)).network();
    wordRows = network.wordRows(THE_DOG_BARKS_TOKENS);
    tagRows = network.tagRows(THE_DOG_BARKS_TAGS);
  }

  /**
   * Checks the start configuration: the artificial root on top of an empty stack, the
   * whole sentence in the buffer, and no dependents.
   */
  @Test
  void testStartConfigurationSlots() {
    final int[] expected = new int[FeedforwardContext.FEATURE_COUNT];
    // word slots: s0, s1, s2, b0, b1, b2, then eight absent dependent positions
    final int[] words = {network.rootWordRow(), network.noneWordRow(), network.noneWordRow(),
        wordRows[0], wordRows[1], wordRows[2]};
    final int[] tags = {network.rootTagRow(), network.noneTagRow(), network.noneTagRow(),
        tagRows[0], tagRows[1], tagRows[2]};
    for (int i = 0; i < FeedforwardContext.POSITIONS; i++) {
      expected[i] = i < words.length ? words[i] : network.noneWordRow();
      expected[FeedforwardContext.POSITIONS + i] =
          i < tags.length ? tags[i] : network.noneTagRow();
    }
    for (int i = 0; i < FeedforwardContext.LABEL_POSITIONS; i++) {
      expected[2 * FeedforwardContext.POSITIONS + i] = network.labelRow(null);
    }
    final FeedforwardContext context =
        new FeedforwardContext(network, THE_DOG_BARKS_TOKENS, THE_DOG_BARKS_TAGS);
    assertArrayEquals(expected, context.featureIds(new ArcStandardState(3)));
  }

  /**
   * Checks a configuration with a dependent: after shifting "the" and "dog" and
   * attaching the first to the second as {@code det}, the stack holds the root and
   * "dog", the buffer holds "barks", and "the" is the only dependent of the top.
   */
  @Test
  void testSlotsAfterAttachingADependent() {
    final ArcStandardState state = new ArcStandardState(3);
    state.apply(Transition.SHIFT);
    state.apply(Transition.SHIFT);
    state.apply(Transition.leftArc("det"));

    final int none = network.noneWordRow();
    final int[] expectedWords = {wordRows[1], network.rootWordRow(), none, wordRows[2], none,
        none, wordRows[0], wordRows[0], none, none, none, none, none, none};
    final int noneTag = network.noneTagRow();
    final int[] expectedTags = {tagRows[1], network.rootTagRow(), noneTag, tagRows[2], noneTag,
        noneTag, tagRows[0], tagRows[0], noneTag, noneTag, noneTag, noneTag, noneTag, noneTag};
    final int[] expectedLabels = new int[FeedforwardContext.LABEL_POSITIONS];
    Arrays.fill(expectedLabels, network.labelRow(null));
    // "the" is both the leftmost and the rightmost dependent of "dog"
    expectedLabels[0] = network.labelRow("det");
    expectedLabels[1] = network.labelRow("det");

    final int[] features = new FeedforwardContext(network, THE_DOG_BARKS_TOKENS,
        THE_DOG_BARKS_TAGS).featureIds(state);
    assertEquals(FeedforwardContext.FEATURE_COUNT, features.length);
    assertArrayEquals(expectedWords,
        Arrays.copyOfRange(features, 0, FeedforwardContext.POSITIONS));
    assertArrayEquals(expectedTags, Arrays.copyOfRange(features,
        FeedforwardContext.POSITIONS, 2 * FeedforwardContext.POSITIONS));
    assertArrayEquals(expectedLabels, Arrays.copyOfRange(features,
        2 * FeedforwardContext.POSITIONS, FeedforwardContext.FEATURE_COUNT));
  }
}
