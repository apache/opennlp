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

package opennlp.tools.ml.perceptron;

import java.util.Arrays;
import java.util.Objects;

import opennlp.tools.ml.ArrayMath;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.EvalParameters;
import opennlp.tools.ml.model.MaxentModel;

/**
 * A {@link MaxentModel model} implementation based one the perceptron algorithm.
 * <p>
 * Each outcome is represented as a binary perceptron classifier.
 * This supports standard (integer) weighting as well average weighting as described in:
 * Discriminative Training Methods for Hidden Markov Models: Theory and Experiments
 * with the Perceptron Algorithm. Michael Collins, EMNLP 2002.
 */
public class PerceptronModel extends AbstractModel {

  /**
   * Initializes a {@link PerceptronModel}.
   *
   * @param params The {@link Context parameters} to set.
   * @param predLabels The predicted labels.
   * @param outcomeNames The names of the outcomes.
   */
  public PerceptronModel(Context[] params, String[] predLabels, String[] outcomeNames) {
    super(params,predLabels,outcomeNames);
    modelType = ModelType.Perceptron;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double[] eval(String[] context) {
    return eval(context,new double[evalParams.getNumOutcomes()]);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double[] eval(String[] context, float[] values) {
    return eval(context,values,new double[evalParams.getNumOutcomes()]);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double[] eval(String[] context, double[] probs) {
    return eval(context,null,probs);
  }

  public double[] eval(String[] context, float[] values, double[] outsums) {
    Context[] scontexts = new Context[context.length];
    java.util.Arrays.fill(outsums, 0);
    for (int i = 0; i < context.length; i++) {
      scontexts[i] = pmap.get(context[i]);
    }
    return eval(scontexts,values,outsums,evalParams,true);
  }

  /**
   * Evaluates a {@link PerceptronModel}.
   * 
   * @param context The context parameters as {@code int[]}.
   * @param prior The data prior to the evaluation as {@code double[]}.
   * @param model The {@link EvalParameters} used for evaluation.
   *
   * @return The resulting evaluation data as {@code double[]}.
   */
  public static double[] eval(int[] context, double[] prior, EvalParameters model) {
    return eval(context,null,prior,model,true);
  }

  /**
   * Evaluates a {@link PerceptronModel}.
   *
   * @param context The context parameters as {@code int[]}.
   * @param values The {@code float[]} values to be used.
   * @param prior The data prior to the evaluation as {@code double[]}.
   * @param model The {@link EvalParameters} used for evaluation.
   * @param normalize Whether to normalize, or not.
   *
   * @return The resulting evaluation data as {@code double[]}.
   */
  static double[] eval(int[] context, float[] values, double[] prior,
                       EvalParameters model, boolean normalize) {
    Context[] scontexts = new Context[context.length];
    for (int i = 0; i < context.length; i++) {
      scontexts[i] = model.getParams()[context[i]];
    }

    return eval(scontexts, values, prior, model, normalize);
  }

  /**
   * Evaluates a {@link PerceptronModel}.
   *
   * @param context The {@link Context[] parameters} to set..
   * @param values The {@code float[]} values to be used.
   * @param prior The data prior to the evaluation as {@code double[]}.
   * @param model The {@link EvalParameters} used for evaluation.
   * @param normalize Whether to normalize, or not.
   *
   * @return The resulting evaluation data as {@code double[]}.
   */
  static double[] eval(Context[] context, float[] values, double[] prior,
                       EvalParameters model, boolean normalize) {

    ArrayMath.sumFeatures(context, values, prior);

    if (normalize) {
      int numOutcomes = model.getNumOutcomes();

      double maxPrior = 1;

      for (int oid = 0; oid < numOutcomes; oid++) {
        if (maxPrior < StrictMath.abs(prior[oid]))
          maxPrior = StrictMath.abs(prior[oid]);
      }

      double normal = 0.0;
      for (int oid = 0; oid < numOutcomes; oid++) {
        prior[oid] = StrictMath.exp(prior[oid] / maxPrior);
        normal += prior[oid];
      }

      for (int oid = 0; oid < numOutcomes; oid++) {
        prior[oid] /= normal;
      }
    }
    return prior;
  }

  @Override
  public int hashCode() {
    /*
     * Note:
     * The hashcode for 'pmap' can not be used here, as PerceptronModelWriter
     * uses compressions during sortValues() operation, quote:
     * "remove parameters with 0 weight and predicates with no parameters"
     *
     * This leads to fewer entries in 'pmap' for serialized PerceptronModel instances
     * that were trained from scratch.
     */
    return Objects.hash(Arrays.hashCode(outcomeNames), evalParams, prior);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof PerceptronModel model) {

      /*
       * Note:
       * The comparison 'pmap.equals(model.pmap)' can not be made here, as PerceptronModelWriter
       * uses compressions during sortValues() operation, quote:
       * "remove parameters with 0 weight and predicates with no parameters"
       *
       * This leads to fewer entries in 'pmap' for serialized PerceptronModel instances
       * that were trained from scratch.
       */
      return Objects.deepEquals(outcomeNames, model.outcomeNames)
              && Objects.equals(prior, model.prior);
    }

    return false;
  }
}
