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

import opennlp.tools.ml.model.Context;
import opennlp.tools.ml.model.EvalParameters;

/**
 * Parameters for the evalution of a naive bayes classifier
 */
public class NaiveBayesEvalParameters extends EvalParameters {

  protected double[] outcomeTotals;
  protected long vocabulary;

  public NaiveBayesEvalParameters(Context[] params, int numOutcomes,
      double[] outcomeTotals, long vocabulary) {
    super(params, numOutcomes);
    this.outcomeTotals = outcomeTotals;
    this.vocabulary = vocabulary;
  }

  public double[] getOutcomeTotals() {
    return outcomeTotals;
  }

  public long getVocabulary() {
    return vocabulary;
  }

}
