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

package opennlp.tools.ml.naivebayes;

import java.util.Map;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.EvalParameters;
import opennlp.tools.ml.model.MaxentModel;

/**
 * A {@link MaxentModel} implementation of the multinomial Naive Bayes classifier model.
 *
 * @see AbstractModel
 * @see MaxentModel
 */
public class NaiveBayesModel extends AbstractModel {

  protected double[] outcomeTotals;
  protected long vocabulary;

  /**
   * Initializes a {@link NaiveBayesModel}.
   *
   * @param params The {@link Context parameters} to set.
   * @param predLabels The predicted labels.
   * @param pmap A {@link Map} that provides a mapping between predicates and contexts.
   * @param outcomeNames The names of the outcomes.
   */
  NaiveBayesModel(Context[] params, String[] predLabels, Map<String, Context> pmap,
                  String[] outcomeNames) {
    super(params, predLabels, pmap, outcomeNames);
    outcomeTotals = initOutcomeTotals(outcomeNames, params);
    this.evalParams = new NaiveBayesEvalParameters(params, outcomeNames.length,
        outcomeTotals, predLabels.length);
    modelType = ModelType.NaiveBayes;
  }

  /**
   * Initializes a {@link NaiveBayesModel}.
   *
   * @param params The {@link Context parameters} to set.
   * @param predLabels The predicted labels.
   * @param outcomeNames The names of the outcomes.
   */
  public NaiveBayesModel(Context[] params, String[] predLabels, String[] outcomeNames) {
    super(params, predLabels, outcomeNames);
    outcomeTotals = initOutcomeTotals(outcomeNames, params);
    this.evalParams = new NaiveBayesEvalParameters(params, outcomeNames.length,
        outcomeTotals, predLabels.length);
    modelType = ModelType.NaiveBayes;
  }

  protected double[] initOutcomeTotals(String[] outcomeNames, Context[] params) {
    double[] outcomeTotals = new double[outcomeNames.length];
    for (Context context : params) {
      for (int j = 0; j < context.getOutcomes().length; ++j) {
        int outcome = context.getOutcomes()[j];
        double count = context.getParameters()[j];
        outcomeTotals[outcome] += count;
      }
    }
    return outcomeTotals;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double[] eval(String[] context) {
    return eval(context, new double[evalParams.getNumOutcomes()]);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double[] eval(String[] context, float[] values) {
    return eval(context, values, new double[evalParams.getNumOutcomes()]);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double[] eval(String[] context, double[] probs) {
    return eval(context, null, probs);
  }

  public double[] eval(String[] context, float[] values, double[] outsums) {
    Context[] scontexts = new Context[context.length];
    java.util.Arrays.fill(outsums, 0);
    for (int i = 0; i < context.length; i++) {
      scontexts[i] = pmap.get(context[i]);
    }
    return eval(scontexts, values, outsums, evalParams, true);
  }

  /**
   * Evaluates a {@link NaiveBayesModel}.
   *
   * @param context The context parameters as {@code int[]}.
   * @param prior The data prior to the evaluation as {@code double[]}.
   * @param model The {@link EvalParameters} used for evaluation.
   *
   * @return The resulting evaluation data as {@code double[]}.
   */
  public static double[] eval(int[] context, double[] prior, EvalParameters model) {
    return eval(context, null, prior, model, true);
  }

  /**
   * Evaluates a {@link NaiveBayesModel}.
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
    Probabilities<Integer> probabilities = new LogProbabilities<>();
    double[] outcomeTotals = model instanceof NaiveBayesEvalParameters
        ? ((NaiveBayesEvalParameters) model).getOutcomeTotals() : new double[prior.length];
    long vocabulary = model instanceof NaiveBayesEvalParameters
        ? ((NaiveBayesEvalParameters) model).getVocabulary() : 0;
    double[] activeParameters;
    int[] activeOutcomes;
    double value = 1;
    for (int ci = 0; ci < context.length; ci++) {
      if (context[ci] != null) {
        Context predParams = context[ci];
        activeOutcomes = predParams.getOutcomes();
        activeParameters = predParams.getParameters();
        if (values != null) {
          value = values[ci];
        }
        int ai = 0;
        for (int i = 0; i < outcomeTotals.length && ai < activeOutcomes.length; ++i) {
          int oid = activeOutcomes[ai];
          double numerator = oid == i ? activeParameters[ai++] * value : 0;
          double denominator = outcomeTotals[i];
          probabilities.addIn(i, getProbability(numerator, denominator, vocabulary, true), 1);
        }
      }
    }
    double total = 0;
    for (double outcomeTotal : outcomeTotals) {
      total += outcomeTotal;
    }
    for (int i = 0; i < outcomeTotals.length; ++i) {
      double numerator = outcomeTotals[i];
      probabilities.addIn(i, numerator / total, 1);
    }
    for (int i = 0; i < outcomeTotals.length; ++i) {
      prior[i] = probabilities.get(i);
    }
    return prior;
  }


  /**
   * Evaluates a {@link NaiveBayesModel}.
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

  private static double getProbability(double numerator, double denominator,
                                       double vocabulary, boolean isSmoothed) {
    if (isSmoothed)
      return getSmoothedProbability(numerator, denominator, vocabulary);
    else if (denominator == 0 || denominator <= Double.MIN_VALUE)
      return 0;
    else
      return numerator / denominator;
  }

  private static double getSmoothedProbability(double numerator, double denominator, double vocabulary) {
    final double delta = 0.05; // Lidstone smoothing

    return (numerator + delta) / (denominator + delta * vocabulary);
  }
}
