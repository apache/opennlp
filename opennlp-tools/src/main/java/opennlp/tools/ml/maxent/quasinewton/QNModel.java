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

package opennlp.tools.ml.maxent.quasinewton;

import opennlp.tools.ml.ArrayMath;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Context;

public class QNModel extends AbstractModel {

  public QNModel(Context[] params, String[] predLabels, String[] outcomeNames) {
    super(params, predLabels, outcomeNames);
    this.modelType = ModelType.MaxentQn;
  }

  public int getNumOutcomes() {
    return this.outcomeNames.length;
  }

  private Context getPredIndex(String predicate) {
    return pmap.get(predicate);
  }

  public double[] eval(String[] context) {
    return eval(context, new double[evalParams.getNumOutcomes()]);
  }

  public double[] eval(String[] context, double[] probs) {
    return eval(context, null, probs);
  }

  public double[] eval(String[] context, float[] values) {
    return eval(context, values, new double[evalParams.getNumOutcomes()]);
  }

  /**
   * Model evaluation which should be used during inference.
   * @param context
   *          The predicates which have been observed at the present
   *          decision point.
   * @param values
   *          Weights of the predicates which have been observed at
   *          the present decision point.
   * @param probs
   *          Probability for outcomes.
   * @return Normalized probabilities for the outcomes given the context.
   */
  private double[] eval(String[] context, float[] values, double[] probs) {

    for (int ci = 0; ci < context.length; ci++) {
      Context pred = getPredIndex(context[ci]);

      if (pred != null) {
        double predValue = 1.0;
        if (values != null) predValue = values[ci];

        double[] parameters = pred.getParameters();
        int[] outcomes = pred.getOutcomes();
        for (int i = 0; i < outcomes.length; i++) {
          int oi = outcomes[i];
          probs[oi] += predValue * parameters[i];
        }
      }
    }

    double logSumExp = ArrayMath.logSumOfExps(probs);
    for (int oi = 0; oi < outcomeNames.length; oi++) {
      probs[oi] = Math.exp(probs[oi] - logSumExp);
    }
    return probs;
  }

  /**
   * Model evaluation which should be used during training to report model accuracy.
   * @param context
   *          Indices of the predicates which have been observed at the present
   *          decision point.
   * @param values
   *          Weights of the predicates which have been observed at
   *          the present decision point.
   * @param probs
   *          Probability for outcomes
   * @param nOutcomes
   *          Number of outcomes
   * @param nPredLabels
   *          Number of unique predicates
   * @param parameters
   *          Model parameters
   * @return Normalized probabilities for the outcomes given the context.
   */
  static double[] eval(int[] context, float[] values, double[] probs,
      int nOutcomes, int nPredLabels, double[] parameters) {

    for (int i = 0; i < context.length; i++) {
      int predIdx = context[i];
      double predValue = values != null ? values[i] : 1.0;
      for (int oi = 0; oi < nOutcomes; oi++) {
        probs[oi] += predValue * parameters[oi * nPredLabels + predIdx];
      }
    }

    double logSumExp = ArrayMath.logSumOfExps(probs);

    for (int oi = 0; oi < nOutcomes; oi++) {
      probs[oi] = Math.exp(probs[oi] - logSumExp);
    }

    return probs;
  }
}
