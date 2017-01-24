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

/**
 * Class implementing the multinomial Naive Bayes classifier model.
 */
public class NaiveBayesModel extends AbstractModel {

  protected double[] outcomeTotals;
  protected long vocabulary;

  public NaiveBayesModel(Context[] params, String[] predLabels, Map<String, Integer> pmap,
                         String[] outcomeNames) {
    super(params, predLabels, pmap, outcomeNames);
    outcomeTotals = initOutcomeTotals(outcomeNames, params);
    this.evalParams = new NaiveBayesEvalParameters(params, outcomeNames.length,
        outcomeTotals, predLabels.length);
    modelType = ModelType.NaiveBayes;
  }

  public NaiveBayesModel(Context[] params, String[] predLabels, String[] outcomeNames) {
    super(params, predLabels, outcomeNames);
    outcomeTotals = initOutcomeTotals(outcomeNames, params);
    this.evalParams = new NaiveBayesEvalParameters(params, outcomeNames.length,
        outcomeTotals, predLabels.length);
    modelType = ModelType.NaiveBayes;
  }

  protected double[] initOutcomeTotals(String[] outcomeNames, Context[] params) {
    double[] outcomeTotals = new double[outcomeNames.length];
    for (int i = 0; i < params.length; ++i) {
      Context context = params[i];
      for (int j = 0; j < context.getOutcomes().length; ++j) {
        int outcome = context.getOutcomes()[j];
        double count = context.getParameters()[j];
        outcomeTotals[outcome] += count;
      }
    }
    return outcomeTotals;
  }

  public double[] eval(String[] context) {
    return eval(context, new double[evalParams.getNumOutcomes()]);
  }

  public double[] eval(String[] context, float[] values) {
    return eval(context, values, new double[evalParams.getNumOutcomes()]);
  }

  public double[] eval(String[] context, double[] probs) {
    return eval(context, null, probs);
  }

  public double[] eval(String[] context, float[] values, double[] outsums) {
    int[] scontexts = new int[context.length];
    java.util.Arrays.fill(outsums, 0);
    for (int i = 0; i < context.length; i++) {
      Integer ci = pmap.get(context[i]);
      scontexts[i] = ci == null ? -1 : ci;
    }
    return eval(scontexts, values, outsums, evalParams, true);
  }

  public static double[] eval(int[] context, double[] prior, EvalParameters model) {
    return eval(context, null, prior, model, true);
  }

  public static double[] eval(int[] context, float[] values, double[] prior,
                              EvalParameters model, boolean normalize) {
    Probabilities<Integer> probabilities = new LogProbabilities<>();
    Context[] params = model.getParams();
    double[] outcomeTotals = model instanceof NaiveBayesEvalParameters
        ? ((NaiveBayesEvalParameters) model).getOutcomeTotals() : new double[prior.length];
    long vocabulary = model instanceof NaiveBayesEvalParameters
        ? ((NaiveBayesEvalParameters) model).getVocabulary() : 0;
    double[] activeParameters;
    int[] activeOutcomes;
    double value = 1;
    for (int ci = 0; ci < context.length; ci++) {
      if (context[ci] >= 0) {
        Context predParams = params[context[ci]];
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
    for (int i = 0; i < outcomeTotals.length; ++i) {
      total += outcomeTotals[i];
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

  private static double getProbability(double numerator, double denominator,
                                       double vocabulary, boolean isSmoothed) {
    if (isSmoothed)
      return getSmoothedProbability(numerator, denominator, vocabulary);
    else if (denominator == 0 || denominator < Double.MIN_VALUE)
      return 0;
    else
      return 1.0 * numerator / denominator;
  }

  private static double getSmoothedProbability(double numerator, double denominator, double vocabulary) {
    final double delta = 0.05; // Lidstone smoothing

    return 1.0 * (numerator + delta) / (denominator + delta * vocabulary);
  }
}
