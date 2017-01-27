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

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.EvalParameters;
import opennlp.tools.ml.model.Prior;
import opennlp.tools.ml.model.UniformPrior;

/**
 * A maximum entropy model which has been trained using the Generalized
 * Iterative Scaling procedure (implemented in GIS.java).
 */
public final class GISModel extends AbstractModel {

  /**
   * Creates a new model with the specified parameters, outcome names, and
   * predicate/feature labels.
   *
   * @param params
   *          The parameters of the model.
   * @param predLabels
   *          The names of the predicates used in this model.
   * @param outcomeNames
   *          The names of the outcomes this model predicts.
   */
  public GISModel(Context[] params, String[] predLabels, String[] outcomeNames) {
    this(params, predLabels, outcomeNames, new UniformPrior());
  }

  /**
   * Creates a new model with the specified parameters, outcome names, and
   * predicate/feature labels.
   *
   * @param params
   *          The parameters of the model.
   * @param predLabels
   *          The names of the predicates used in this model.
   * @param outcomeNames
   *          The names of the outcomes this model predicts.
   * @param prior
   *          The prior to be used with this model.
   */
  public GISModel(Context[] params, String[] predLabels, String[] outcomeNames, Prior prior) {
    super(params, predLabels, outcomeNames);
    this.prior = prior;
    prior.setLabels(outcomeNames, predLabels);
    modelType = ModelType.Maxent;
  }

  /**
   * Use this model to evaluate a context and return an array of the likelihood
   * of each outcome given that context.
   *
   * @param context
   *          The names of the predicates which have been observed at the
   *          present decision point.
   * @return The normalized probabilities for the outcomes given the context.
   *         The indexes of the double[] are the outcome ids, and the actual
   *         string representation of the outcomes can be obtained from the
   *         method getOutcome(int i).
   */
  public final double[] eval(String[] context) {
    return (eval(context, new double[evalParams.getNumOutcomes()]));
  }

  public final double[] eval(String[] context, float[] values) {
    return (eval(context, values, new double[evalParams.getNumOutcomes()]));
  }

  public final double[] eval(String[] context, double[] outsums) {
    return eval(context, null, outsums);
  }

  /**
   * Use this model to evaluate a context and return an array of the likelihood
   * of each outcome given that context.
   *
   * @param context
   *          The names of the predicates which have been observed at the
   *          present decision point.
   * @param outsums
   *          This is where the distribution is stored.
   * @return The normalized probabilities for the outcomes given the context.
   *         The indexes of the double[] are the outcome ids, and the actual
   *         string representation of the outcomes can be obtained from the
   *         method getOutcome(int i).
   */
  public final double[] eval(String[] context, float[] values, double[] outsums) {
    int[] scontexts = new int[context.length];
    for (int i = 0; i < context.length; i++) {
      Integer ci = pmap.get(context[i]);
      scontexts[i] = ci == null ? -1 : ci;
    }
    prior.logPrior(outsums, scontexts, values);
    return GISModel.eval(scontexts, values, outsums, evalParams);
  }


  /**
   * Use this model to evaluate a context and return an array of the likelihood
   * of each outcome given the specified context and the specified parameters.
   *
   * @param context
   *          The integer values of the predicates which have been observed at
   *          the present decision point.
   * @param prior
   *          The prior distribution for the specified context.
   * @param model
   *          The set of parametes used in this computation.
   * @return The normalized probabilities for the outcomes given the context.
   *         The indexes of the double[] are the outcome ids, and the actual
   *         string representation of the outcomes can be obtained from the
   *         method getOutcome(int i).
   */
  public static double[] eval(int[] context, double[] prior,
      EvalParameters model) {
    return eval(context, null, prior, model);
  }

  /**
   * Use this model to evaluate a context and return an array of the likelihood
   * of each outcome given the specified context and the specified parameters.
   *
   * @param context
   *          The integer values of the predicates which have been observed at
   *          the present decision point.
   * @param values
   *          The values for each of the parameters.
   * @param prior
   *          The prior distribution for the specified context.
   * @param model
   *          The set of parametes used in this computation.
   * @return The normalized probabilities for the outcomes given the context.
   *         The indexes of the double[] are the outcome ids, and the actual
   *         string representation of the outcomes can be obtained from the
   *         method getOutcome(int i).
   */
  public static double[] eval(int[] context, float[] values, double[] prior,
      EvalParameters model) {
    Context[] params = model.getParams();
    int numfeats[] = new int[model.getNumOutcomes()];
    int[] activeOutcomes;
    double[] activeParameters;
    double value = 1;
    for (int ci = 0; ci < context.length; ci++) {
      if (context[ci] >= 0) {
        Context predParams = params[context[ci]];
        activeOutcomes = predParams.getOutcomes();
        activeParameters = predParams.getParameters();
        if (values != null) {
          value = values[ci];
        }
        for (int ai = 0; ai < activeOutcomes.length; ai++) {
          int oid = activeOutcomes[ai];
          numfeats[oid]++;
          prior[oid] += activeParameters[ai] * value;
        }
      }
    }

    double normal = 0.0;
    for (int oid = 0; oid < model.getNumOutcomes(); oid++) {
      prior[oid] = Math.exp(prior[oid]);
      normal += prior[oid];
    }

    for (int oid = 0; oid < model.getNumOutcomes(); oid++) {
      prior[oid] /= normal;
    }
    return prior;
  }
}
