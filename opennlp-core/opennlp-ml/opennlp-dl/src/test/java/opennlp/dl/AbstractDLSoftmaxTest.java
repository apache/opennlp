/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.dl;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractDLSoftmaxTest {

  private static final float NAN = Float.NaN;
  private static final float POS_INF = Float.POSITIVE_INFINITY;
  private static final float NEG_INF = Float.NEGATIVE_INFINITY;

  static Stream<Arguments> distributions() {
    return Stream.of(
        Arguments.of(new float[] {}, new double[] {}),
        Arguments.of(new float[] {5f}, new double[] {1d}),
        Arguments.of(new float[] {0f, 0f, 0f}, new double[] {1d / 3, 1d / 3, 1d / 3}),
        Arguments.of(new float[] {1f, 2f, 3f},
            new double[] {0.09003057317038046, 0.24472847105479764, 0.6652409557748219}),
        Arguments.of(new float[] {1000f, 1001f},
            new double[] {0.2689414213699951, 0.7310585786300049}),
        Arguments.of(new float[] {0f, NEG_INF, 0f}, new double[] {0.5, 0d, 0.5}),
        Arguments.of(new float[] {0f, POS_INF, POS_INF, NAN}, new double[] {0d, 0.5, 0.5, 0d}),
        Arguments.of(new float[] {NEG_INF, NAN}, new double[] {0.5, 0.5}),
        Arguments.of(new float[] {0f, NAN, 0f}, new double[] {0.5, Double.NaN, 0.5}));
  }

  @ParameterizedTest
  @MethodSource("distributions")
  void testSoftmaxProbabilities(float[] scores, double[] expected) {
    assertArrayEquals(expected, AbstractDL.softmaxProbabilities(scores), 1e-12);
  }

  @ParameterizedTest
  @MethodSource("distributions")
  void testSoftmaxProbabilityMatchesDistribution(float[] scores, double[] expected) {
    for (int i = 0; i < scores.length; i++) {
      assertEquals(expected[i], AbstractDL.softmaxProbability(scores, i), 1e-12);
    }
  }

  @Test
  void testSoftmaxProbabilityRejectsNullScores() {
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> AbstractDL.softmaxProbability(null, 0));
    assertEquals("The scores must not be null.", e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 3})
  void testSoftmaxProbabilityRejectsIndexOutOfRange(int index) {
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> AbstractDL.softmaxProbability(new float[] {1f, 2f, 3f}, index));
    assertEquals("The index " + index + " is out of range for 3 scores.", e.getMessage());
  }

  @Test
  void testSoftmaxProbabilitiesStaysFiniteForLargeScores() {
    final double[] out =
        AbstractDL.softmaxProbabilities(new float[] {Float.MAX_VALUE, Float.MAX_VALUE});

    assertArrayEquals(new double[] {0.5, 0.5}, out, 1e-12);
  }

  @Test
  void testSoftmaxProbabilitiesDoesNotModifyScores() {
    final float[] scores = {1f, 2f, 3f};

    AbstractDL.softmaxProbabilities(scores);

    assertArrayEquals(new float[] {1f, 2f, 3f}, scores);
  }

  @Test
  void testSoftmaxProbabilitiesRejectsNullScores() {
    final IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> AbstractDL.softmaxProbabilities(null));
    assertEquals("The scores must not be null.", e.getMessage());
  }
}
