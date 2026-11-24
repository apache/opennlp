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

package opennlp.tools.ml.maxent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.Prior;

class GISModelTest {

  @Test
  void testVeryNegativeWeights() {
    assertProbabilities(-1000, -999);
  }

  @Test
  void testVeryPositiveWeights() {
    assertProbabilities(1000, 999);
  }

  @Test
  void testOrdinaryWeights() {
    assertProbabilities(1, 2);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-744, 0, 700})
  void testFinitePositiveSumPreservesLegacyProbabilityBits(double offset) {
    double[] weights = {1 + offset, 3 + offset, 2 + offset};
    Context[] params = {new Context(new int[] {0, 1, 2}, weights)};
    GISModel model = new GISModel(params, new String[] {"feature"},
        new String[] {"first", "second", "third"});

    double[] probabilities = model.eval(new String[] {"feature"});

    // Include the default UniformPrior and preserve the order of the legacy operations.
    double logPrior = StrictMath.log(1.0 / weights.length);
    double[] exponentials = new double[weights.length];
    double sum = 0.0;
    for (int i = 0; i < weights.length; i++) {
      exponentials[i] = StrictMath.exp(logPrior + weights[i]);
      sum += exponentials[i];
    }

    Assertions.assertTrue(sum > 0.0 && Double.isFinite(sum));
    for (int i = 0; i < weights.length; i++) {
      Assertions.assertEquals(Double.doubleToRawLongBits(exponentials[i] / sum),
          Double.doubleToRawLongBits(probabilities[i]));
    }
  }

  @Test
  void testFiniteExponentialsWithOverflowingSum() {
    // Each exp(710 + log(0.5)) is finite, but their sum overflows.
    Context[] params = {new Context(new int[] {0, 1}, new double[] {710, 710})};
    GISModel model = new GISModel(params, new String[] {"feature"}, new String[] {"first", "second"});

    Assertions.assertArrayEquals(new double[] {0.5, 0.5}, model.eval(new String[] {"feature"}));
  }

  @ParameterizedTest
  @ValueSource(doubles = {-1000, 1000})
  void testEqualLargeScoresGiveUniformDistribution(double score) {
    Context[] params = {new Context(new int[] {0, 1}, new double[] {score, score})};
    GISModel model = new GISModel(params, new String[] {"feature"}, new String[] {"first", "second"});

    double[] probabilities = model.eval(new String[] {"feature"});

    Assertions.assertEquals(0.5, probabilities[0], 1e-12);
    Assertions.assertEquals(0.5, probabilities[1], 1e-12);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-1000, 0, 1000})
  void testSingleOutcomeHasProbabilityOne(double score) {
    Context[] params = {new Context(new int[] {0}, new double[] {score})};
    GISModel model = new GISModel(params, new String[] {"feature"}, new String[] {"only"});

    double[] probabilities = model.eval(new String[] {"feature"});

    Assertions.assertEquals(1, probabilities.length);
    Assertions.assertEquals(1.0, probabilities[0], 0.0);
    Assertions.assertEquals("only", model.getBestOutcome(probabilities));
  }

  /**
   * Scores 1, 3 and 2 give the same probabilities whatever constant is added to all of them,
   * and the best outcome is not the first one.
   */
  @ParameterizedTest
  @ValueSource(doubles = {-1000, 0, 1000})
  void testThreeOutcomesAreNormalizedAndShiftInvariant(double offset) {
    Context[] params = {
        new Context(new int[] {0, 1, 2}, new double[] {1 + offset, 3 + offset, 2 + offset})};
    GISModel model = new GISModel(params, new String[] {"feature"},
        new String[] {"first", "second", "third"});

    double[] probabilities = model.eval(new String[] {"feature"});

    Assertions.assertEquals(0.09003057317038046, probabilities[0], 1e-10);
    Assertions.assertEquals(0.6652409557748218, probabilities[1], 1e-10);
    Assertions.assertEquals(0.24472847105479764, probabilities[2], 1e-10);
    Assertions.assertEquals(1.0, probabilities[0] + probabilities[1] + probabilities[2], 1e-12);
    Assertions.assertEquals("second", model.getBestOutcome(probabilities));
  }

  /**
   * Feature values 2.0 and 0.5 with the fixed prior 0.5, 0, 0.3 and 0.2: the outcome with
   * a zero prior keeps probability 0, and adding the same constant to every parameter
   * (which shifts every score by 2.5 times that constant) does not change the distribution.
   */
  @ParameterizedTest
  @ValueSource(doubles = {-1000, 0, 1000})
  void testWeightedFeaturesAndNonUniformPriorsWithZeroPriorOutcome(double offset) {
    Context[] params = {
        new Context(new int[] {0, 1, 2, 3},
            new double[] {0.5 + offset, 2.0 + offset, 1.0 + offset, 3.0 + offset}),
        new Context(new int[] {0, 1, 2, 3},
            new double[] {0.5 + offset, -0.5 + offset, 1.5 + offset, offset})};
    GISModel model = new GISModel(params, new String[] {"a", "b"},
        new String[] {"first", "second", "third", "fourth"}, new FixedPrior(0.5, 0.0, 0.3, 0.2));

    double[] probabilities = model.eval(new String[] {"a", "b"}, new float[] {2.0f, 0.5f});

    Assertions.assertEquals(0.020030956941326693, probabilities[0], 1e-9);
    Assertions.assertEquals(0.0, probabilities[1], 0.0);
    Assertions.assertEquals(0.05386351247541375, probabilities[2], 1e-9);
    Assertions.assertEquals(0.9261055305832596, probabilities[3], 1e-9);
    Assertions.assertEquals(1.0,
        probabilities[0] + probabilities[1] + probabilities[2] + probabilities[3], 1e-12);
    Assertions.assertEquals("fourth", model.getBestOutcome(probabilities));
  }

  private void assertProbabilities(double first, double second) {
    Context[] params = {new Context(new int[] {0, 1}, new double[] {first, second})};
    GISModel model = new GISModel(params, new String[] {"feature"}, new String[] {"first", "second"});

    double[] probabilities = model.eval(new String[] {"feature"});
    double expectedFirst = 1.0 / (1.0 + StrictMath.exp(second - first));

    Assertions.assertEquals(expectedFirst, probabilities[0], 1e-12);
    Assertions.assertEquals(1.0 - expectedFirst, probabilities[1], 1e-12);
    Assertions.assertEquals(1.0, probabilities[0] + probabilities[1], 1e-12);
  }

  /** A {@link Prior} with fixed outcome probabilities, independent of the context. */
  private static final class FixedPrior implements Prior {

    private final double[] logPriors;

    FixedPrior(double... priors) {
      logPriors = new double[priors.length];
      for (int i = 0; i < priors.length; i++) {
        logPriors[i] = StrictMath.log(priors[i]);
      }
    }

    @Override
    public void logPrior(double[] dist, int[] context) {
      System.arraycopy(logPriors, 0, dist, 0, logPriors.length);
    }

    @Override
    public void logPrior(double[] dist, int[] context, float[] values) {
      System.arraycopy(logPriors, 0, dist, 0, logPriors.length);
    }

    @Override
    public void logPrior(double[] dist, Context[] context, float[] values) {
      System.arraycopy(logPriors, 0, dist, 0, logPriors.length);
    }

    @Override
    public void setLabels(String[] outcomeLabels, String[] contextLabels) {
      // the priors are fixed
    }
  }
}
