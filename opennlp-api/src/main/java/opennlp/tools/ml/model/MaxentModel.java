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

package opennlp.tools.ml.model;

/**
 * Interface for maximum entropy models.
 **/
public interface MaxentModel {

  /**
   * Evaluates a {@code context}.
   *
   * @param context An array of String names of the contextual predicates
   *                which are to be evaluated together.
   * @return An array of the probabilities for each of the different
   *         outcomes, all of which sum to {@code 1}.
   *
   **/
  double[] eval(String[] context);

  /**
     * Evaluates a {@code context}.
     *
     * @param context An array of String names of the contextual predicates
     *                which are to be evaluated together.
     * @param probs An array which is populated with the probabilities for each of the different
     *         outcomes, all of which sum to 1.
     * @return An array of the probabilities for each of the different
     *         outcomes, all of which sum to {@code 1}.
     **/
  double[] eval(String[] context, double[] probs);

  /**
   * Evaluates a {@code context} with the specified context {@code values}.
   *
   * @param context An array of String names of the contextual predicates
     *              which are to be evaluated together.
   * @param values The values associated with each context.
   * @return An array of the probabilities for each of the different
   *         outcomes, all of which sum to {@code 1}.
   */
  double[] eval(String[] context, float[] values);

  /**
   * Retrieves the outcome associated with the index
   * containing the highest probability in the double[].
   *
   * @param outcomes A {@code double[]} as returned by the
   *                 {@link #eval(String[])} method.
   * @return The String name of the best outcome.
   **/
  String getBestOutcome(double[] outcomes);

  /**
   * Retrieves a string matching all the outcome names with all the
   * probabilities produced by the {@link #eval(String[])} method.
   *
   * @param outcomes A {@code double[]} as returned by the
   *                 {@link #eval(String[])} method.
   * @return String containing outcome names paired with the normalized
   *         probability (contained in the {@code double[] ocs})
   *         for each one.
   **/
  // TODO: This should be removed, can't be used anyway without format spec
  String getAllOutcomes(double[] outcomes);

  /**
   * Retrieves the String name of the outcome associated with the index {@code i}.
   *
   * @param i The index for which the name of the associated outcome is
   *          desired.
   * @return The String name of the outcome
   **/
  String getOutcome(int i);

  /**
   * Retrieves the index associated with the String name of the given
   * outcome.
   *
   * @param outcome The String name of the outcome for which the
   *                index is desired,
   * @return The index if the given outcome label exists for this
   *         model, {@code -1} if it does not.
   **/
  int getIndex(String outcome);

  /**
   *  @return Retrieves the number of outcomes for this model.
   **/
  int getNumOutcomes();

}
