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

package opennlp.tools.ml;

import opennlp.tools.ml.model.MaxentModel;

/**
 * A deterministic pseudo-random {@link MaxentModel} test fixture. The probability of an
 * outcome is derived from a hash of the joined context strings, the outcome index and a
 * fixed seed with splitmix64-style mixing, so repeated evals of the same context return
 * identical values in (0.01, 0.99]. Values are intentionally not normalized. The
 * {@code eval(context, probs)} buffer contract is honored: values are written into the
 * passed array and that same array is returned. Stateless and therefore thread-safe.
 */
class SeededMaxentModel implements MaxentModel {

  private final String[] outcomes;
  private final long seed;

  /**
   * Initializes a {@link SeededMaxentModel} instance.
   *
   * @param outcomes The outcome labels; the outcome index is the array index.
   * @param seed The seed all probabilities are derived from.
   */
  SeededMaxentModel(String[] outcomes, long seed) {
    this.outcomes = outcomes;
    this.seed = seed;
  }

  /**
   * @return A pseudo-random probability in (0.01, 0.99], a pure function
   *         of {@code context}, {@code outcomeIndex} and the seed.
   */
  private double prob(String[] context, int outcomeIndex) {
    long h = seed;
    for (String c : context) {
      h = mix(h, c.hashCode());
    }
    h = mix(h, outcomeIndex);
    // splitmix64 finalizer for avalanche
    h ^= h >>> 30;
    h *= 0xBF58476D1CE4E5B9L;
    h ^= h >>> 27;
    h *= 0x94D049BB133111EBL;
    h ^= h >>> 31;
    double u = (h >>> 11) * (1.0 / (1L << 53)); // [0, 1)
    return 0.01 + 0.98 * u; // (0.01, 0.99]
  }

  /**
   * @return {@code h} combined with {@code v} FNV-style.
   */
  private static long mix(long h, long v) {
    return (h ^ (v + 0x9E3779B97F4A7C15L)) * 0x100000001B3L;
  }

  @Override
  public double[] eval(String[] context) {
    return eval(context, new double[outcomes.length]);
  }

  @Override
  public double[] eval(String[] context, double[] probs) {
    for (int i = 0; i < outcomes.length; i++) {
      probs[i] = prob(context, i);
    }
    return probs; // buffer contract: write into the passed array AND return it
  }

  @Override
  public double[] eval(String[] context, float[] values) {
    return eval(context);
  }

  @Override
  public String getOutcome(int i) {
    return outcomes[i];
  }

  @Override
  public int getNumOutcomes() {
    return outcomes.length;
  }

  @Override
  public String getAllOutcomes(double[] outcomes) {
    return null;
  }

  @Override
  public String getBestOutcome(double[] outcomes) {
    return null;
  }

  @Override
  public int getIndex(String outcome) {
    for (int i = 0; i < outcomes.length; i++) {
      if (outcomes[i].equals(outcome)) {
        return i;
      }
    }
    return -1;
  }
}
