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
   * Evaluates a context.
   *
   * @param context A list of String names of the contextual predicates
   *                which are to be evaluated together.
   * @return an array of the probabilities for each of the different
   *         outcomes, all of which sum to 1.
   *
   **/
  double[] eval(String[] context);

  /**
     * Evaluates a context.
     *
     * @param context A list of String names of the contextual predicates
     *                which are to be evaluated together.
     * @param probs An array which is populated with the probabilities for each of the different
     *         outcomes, all of which sum to 1.
     * @return an array of the probabilities for each of the different outcomes, all of which sum to 1.
     **/
  double[] eval(String[] context, double probs[]);

  /**
   * Evaluates a contexts with the specified context values.
   * @param context A list of String names of the contextual predicates
     *                which are to be evaluated together.
   * @param values The values associated with each context.
   * @return an array of the probabilities for each of the different outcomes, all of which sum to 1.
   */
  double[] eval(String[] context, float[] values);

  /**
   * Simple function to return the outcome associated with the index
   * containing the highest probability in the double[].
   *
   * @param outcomes A <code>double[]</code> as returned by the
   *            <code>eval(String[] context)</code>
   *            method.
   * @return the String name of the best outcome
   **/
  String getBestOutcome(double[] outcomes);

  /**
   * Return a string matching all the outcome names with all the
   * probabilities produced by the <code>eval(String[]
   * context)</code> method.
   *
   * @param outcomes A <code>double[]</code> as returned by the
   *            <code>eval(String[] context)</code>
   *            method.
   * @return    String containing outcome names paired with the normalized
   *            probability (contained in the <code>double[] ocs</code>)
   *            for each one.
   **/
  // TODO: This should be removed, can't be used anyway without format spec
  String getAllOutcomes(double[] outcomes);

  /**
   * Gets the String name of the outcome associated with the index
   * i.
   *
   * @param i the index for which the name of the associated outcome is
   *          desired.
   * @return the String name of the outcome
   **/
  String getOutcome(int i);

  /**
   * Gets the index associated with the String name of the given
   * outcome.
   *
   * @param outcome the String name of the outcome for which the
   *          index is desired
   * @return the index if the given outcome label exists for this
   *     model, -1 if it does not.
   **/
  int getIndex(String outcome);

  /*
   * Returns the data structures relevant to storing the model.
   **/
  // public Object[] getDataStructures();

  /** Returns the number of outcomes for this model.
   *  @return The number of outcomes.
   **/
  int getNumOutcomes();

}
