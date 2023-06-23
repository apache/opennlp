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

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import opennlp.tools.ml.ArrayMath;

/**
 * A basic {@link MaxentModel} implementation.
 */
public abstract class AbstractModel implements MaxentModel {

  /** Mapping between predicates/contexts and an integer representing them. */
  protected Map<String, Context> pmap;
  /** The names of the outcomes. */
  protected String[] outcomeNames;
  /** Parameters for the model. */
  protected EvalParameters evalParams;
  /** Prior distribution for this model. */
  protected Prior prior;

  public enum ModelType { Maxent,Perceptron,MaxentQn,NaiveBayes }

  /** The type of the model. */
  protected ModelType modelType;

  /**
   * Initializes an {@link AbstractModel}.
   *
   * @param params The {@link Context[] parameters} to set.
   * @param predLabels The predicted labels.
   * @param pmap A {@link Map} that provides a mapping between predicates and contexts.
   * @param outcomeNames The names of the outcomes.
   */
  protected AbstractModel(Context[] params, String[] predLabels,
      Map<String, Context> pmap, String[] outcomeNames) {
    this.pmap = pmap;
    this.outcomeNames =  outcomeNames;
    this.evalParams = new EvalParameters(params,outcomeNames.length);
  }

  /**
   * Initializes an {@link AbstractModel}.
   *
   * @param params The {@link Context parameters} to set.
   * @param predLabels The predicted labels.
   * @param outcomeNames The names of the outcomes.
   */
  public AbstractModel(Context[] params, String[] predLabels, String[] outcomeNames) {
    init(predLabels, params, outcomeNames);
    this.evalParams = new EvalParameters(params, outcomeNames.length);
  }

  private void init(String[] predLabels, Context[] params, String[] outcomeNames) {
    this.pmap = new LinkedHashMap<>(predLabels.length);

    for (int i = 0; i < predLabels.length; i++) {
      pmap.put(predLabels[i], params[i]);
    }

    this.outcomeNames =  outcomeNames;
  }


  /**
   * Return the name of the outcome corresponding to the highest likelihood
   * in the parameter ocs.
   *
   * @param ocs A double[] as returned by the eval(String[] context)
   *            method.
   * @return    The name of the most likely outcome.
   */
  @Override
  public final String getBestOutcome(double[] ocs) {
    return outcomeNames[ArrayMath.argmax(ocs)];
  }

  /**
   * @return Retrieves the {@link ModelType}.
   */
  public ModelType getModelType() {
    return modelType;
  }

  /**
   * Retrieves a string matching all the outcome names with all the
   * probabilities produced by the {@link #eval(String[])} method.
   *
   * @param ocs A {@code double[]} as returned by the
   *            {@link #eval(String[])} method.
   * @return    String containing outcome names paired with the normalized
   *            probability (contained in the {@code double[] ocs})
   *            for each one.
   */
  @Override
  public final String getAllOutcomes(double[] ocs) {
    if (ocs.length != outcomeNames.length) {
      return "The double array sent as a parameter to AbstractModel.getAllOutcomes() " +
          "must not have been produced by this model.";
    }
    else {
      DecimalFormat df =  new DecimalFormat("0.0000");
      StringBuilder sb = new StringBuilder(ocs.length * 2);
      sb.append(outcomeNames[0]).append("[").append(df.format(ocs[0])).append("]");
      for (int i = 1; i < ocs.length; i++) {
        sb.append("  ").append(outcomeNames[i]).append("[").append(df.format(ocs[i])).append("]");
      }
      return sb.toString();
    }
  }

  /**
   * @param i An outcome id.
   * @return  Retrieves the name of the outcome associated with that id.
   */
  @Override
  public final String getOutcome(int i) {
    return outcomeNames[i];
  }

  /**
   * @param outcome The String name of the outcome for which the index is desired.
   *
   * @return Retrieves the index if the given {@code outcome} label exists for this
   *         model, {@code -1} if it does not.
   **/
  @Override
  public int getIndex(String outcome) {
    for (int i = 0; i < outcomeNames.length; i++) {
      if (outcomeNames[i].equals(outcome))
        return i;
    }
    return -1;
  }

  @Override
  public int getNumOutcomes() {
    return evalParams.getNumOutcomes();
  }

  /**
   * Provides the fundamental data structures which encode the maxent model
   * information. Note: This method will usually only be needed by
   * {@link opennlp.tools.ml.maxent.io.GISModelWriter GIS model writers}.
   * <p>
   * The following values are held in the Object array which is returned by this method:
   * <ul>
   * <li>index 0: {@link Context} array containing the model parameters.</li>
   * <li>index 1: {@link Map} containing the mapping of model predicates
   *            to unique integers.</li>
   * <li>index 2: {@link String} array containing the names of the outcomes,
   *            stored in the index of the array which represents their
   *            unique ids in the model.</li>
   * </ul>
   *
   * @return An {@link Object} array with the values as described above.
   */
  public final Object[] getDataStructures() {
    Object[] data = new Object[3];
    data[0] = evalParams.getParams();
    data[1] = pmap;
    data[2] = outcomeNames;
    return data;
  }

  @Override
  public int hashCode() {
    return Objects.hash(pmap, Arrays.hashCode(outcomeNames), evalParams, prior);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof AbstractModel model) {

      return pmap.equals(model.pmap) && Objects.deepEquals(outcomeNames, model.outcomeNames)
          && Objects.equals(prior, model.prior);
    }

    return false;
  }
}
