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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractModel implements MaxentModel {

  /** Mapping between predicates/contexts and an integer representing them. */
  protected Map<String, Integer> pmap;
  /** The names of the outcomes. */
  protected String[] outcomeNames;
  /** Parameters for the model. */
  protected EvalParameters evalParams;
  /** Prior distribution for this model. */
  protected Prior prior;

  public enum ModelType { Maxent,Perceptron,MaxentQn,NaiveBayes }

  /** The type of the model. */
  protected ModelType modelType;

  public AbstractModel(Context[] params, String[] predLabels,
      Map<String, Integer> pmap, String[] outcomeNames) {
    this.pmap = pmap;
    this.outcomeNames =  outcomeNames;
    this.evalParams = new EvalParameters(params,outcomeNames.length);
  }

  public AbstractModel(Context[] params, String[] predLabels, String[] outcomeNames) {
    init(predLabels, outcomeNames);
    this.evalParams = new EvalParameters(params, outcomeNames.length);
  }

  private void init(String[] predLabels, String[] outcomeNames) {
    this.pmap = new HashMap<>(predLabels.length);

    for (int i = 0; i < predLabels.length; i++) {
      pmap.put(predLabels[i], i);
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
  public final String getBestOutcome(double[] ocs) {
    int best = 0;
    for (int i = 1; i < ocs.length; i++)
      if (ocs[i] > ocs[best]) best = i;
    return outcomeNames[best];
  }

  public ModelType getModelType() {
    return modelType;
  }

  /**
   * Return a string matching all the outcome names with all the
   * probabilities produced by the <code>eval(String[] context)</code>
   * method.
   *
   * @param ocs A <code>double[]</code> as returned by the
   *            <code>eval(String[] context)</code>
   *            method.
   * @return    String containing outcome names paired with the normalized
   *            probability (contained in the <code>double[] ocs</code>)
   *            for each one.
   */
  public final String getAllOutcomes(double[] ocs) {
    if (ocs.length != outcomeNames.length) {
      return "The double array sent as a parameter to GISModel.getAllOutcomes() " +
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
   * Return the name of an outcome corresponding to an int id.
   *
   * @param i An outcome id.
   * @return  The name of the outcome associated with that id.
   */
  public final String getOutcome(int i) {
    return outcomeNames[i];
  }

  /**
   * Gets the index associated with the String name of the given outcome.
   *
   * @param outcome the String name of the outcome for which the
   *          index is desired
   * @return the index if the given outcome label exists for this
   *     model, -1 if it does not.
   **/
  public int getIndex(String outcome) {
    for (int i = 0; i < outcomeNames.length; i++) {
      if (outcomeNames[i].equals(outcome))
        return i;
    }
    return -1;
  }

  public int getNumOutcomes() {
    return evalParams.getNumOutcomes();
  }

  /**
   * Provides the fundamental data structures which encode the maxent model
   * information.  This method will usually only be needed by
   * GISModelWriters.  The following values are held in the Object array
   * which is returned by this method:
   * <ul>
   * <li>index 0: opennlp.tools.ml.maxent.Context[] containing the model
   *            parameters
   * <li>index 1: java.util.Map containing the mapping of model predicates
   *            to unique integers
   * <li>index 2: java.lang.String[] containing the names of the outcomes,
   *            stored in the index of the array which represents their
   *            unique ids in the model.
   * </ul>
   *
   * @return An Object[] with the values as described above.
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

    if (obj instanceof AbstractModel) {
      AbstractModel model = (AbstractModel) obj;

      return pmap.equals(model.pmap) && Objects.deepEquals(outcomeNames, model.outcomeNames)
          && evalParams.equals(model.evalParams) && Objects.equals(prior, model.prior);
    }

    return false;
  }
}
