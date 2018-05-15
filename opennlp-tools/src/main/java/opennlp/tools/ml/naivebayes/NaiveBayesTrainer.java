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

import java.io.IOException;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.ArrayMath;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.EvalParameters;
import opennlp.tools.ml.model.MutableContext;
import opennlp.tools.util.TrainingParameters;

/**
 * Trains models using the combination of EM algorithm and Naive Bayes classifier
 * which is described in:
 * Text Classification from Labeled and Unlabeled Documents using EM
 * Nigam, McCallum, et al paper of 2000
 */
public class NaiveBayesTrainer extends AbstractEventTrainer {

  public static final String NAIVE_BAYES_VALUE = "NAIVEBAYES";

  /**
   * Number of unique events which occurred in the event set.
   */
  private int numUniqueEvents;
  /**
   * Number of events in the event set.
   */
  private int numEvents;

  /**
   * Number of predicates.
   */
  private int numPreds;
  /**
   * Number of outcomes.
   */
  private int numOutcomes;
  /**
   * Records the array of predicates seen in each event.
   */
  private int[][] contexts;

  /**
   * The value associates with each context. If null then context values are assumes to be 1.
   */
  private float[][] values;

  /**
   * List of outcomes for each event i, in context[i].
   */
  private int[] outcomeList;

  /**
   * Records the num of times an event has been seen for each event i, in context[i].
   */
  private int[] numTimesEventsSeen;

  /**
   * Stores the String names of the outcomes.  The NaiveBayes only tracks outcomes
   * as ints, and so this array is needed to save the model to disk and
   * thereby allow users to know what the outcome was in human
   * understandable terms.
   */
  private String[] outcomeLabels;

  /**
   * Stores the String names of the predicates. The NaiveBayes only tracks
   * predicates as ints, and so this array is needed to save the model to
   * disk and thereby allow users to know what the outcome was in human
   * understandable terms.
   */
  private String[] predLabels;

  public NaiveBayesTrainer() {
  }

  public NaiveBayesTrainer(TrainingParameters parameters) {
    super(parameters);
  }
  
  public boolean isSortAndMerge() {
    return false;
  }

  public AbstractModel doTrain(DataIndexer indexer) throws IOException {
    return this.trainModel(indexer);
  }

  // << members related to AbstractSequenceTrainer

  public AbstractModel trainModel(DataIndexer di) {
    display("Incorporating indexed data for training...  \n");
    contexts = di.getContexts();
    values = di.getValues();
    numTimesEventsSeen = di.getNumTimesEventsSeen();
    numEvents = di.getNumEvents();
    numUniqueEvents = contexts.length;

    outcomeLabels = di.getOutcomeLabels();
    outcomeList = di.getOutcomeList();

    predLabels = di.getPredLabels();
    numPreds = predLabels.length;
    numOutcomes = outcomeLabels.length;

    display("done.\n");

    display("\tNumber of Event Tokens: " + numUniqueEvents + "\n");
    display("\t    Number of Outcomes: " + numOutcomes + "\n");
    display("\t  Number of Predicates: " + numPreds + "\n");

    display("Computing model parameters...\n");

    MutableContext[] finalParameters = findParameters();

    display("...done.\n");

    /* Create and return the model ****/
    return new NaiveBayesModel(finalParameters, predLabels, outcomeLabels);
  }

  private MutableContext[] findParameters() {

    int[] allOutcomesPattern = new int[numOutcomes];
    for (int oi = 0; oi < numOutcomes; oi++)
      allOutcomesPattern[oi] = oi;

    /* Stores the estimated parameter value of each predicate during iteration. */
    MutableContext[] params = new MutableContext[numPreds];
    for (int pi = 0; pi < numPreds; pi++) {
      params[pi] = new MutableContext(allOutcomesPattern, new double[numOutcomes]);
      for (int aoi = 0; aoi < numOutcomes; aoi++)
        params[pi].setParameter(aoi, 0.0);
    }

    EvalParameters evalParams = new EvalParameters(params, numOutcomes);

    double stepSize = 1;

    for (int ei = 0; ei < numUniqueEvents; ei++) {
      int targetOutcome = outcomeList[ei];
      for (int ni = 0; ni < this.numTimesEventsSeen[ei]; ni++) {
        for (int ci = 0; ci < contexts[ei].length; ci++) {
          int pi = contexts[ei][ci];
          if (values == null) {
            params[pi].updateParameter(targetOutcome, stepSize);
          } else {
            params[pi].updateParameter(targetOutcome, stepSize * values[ei][ci]);
          }
        }
      }
    }

    // Output the final training stats.
    trainingStats(evalParams);

    return params;

  }

  private double trainingStats(EvalParameters evalParams) {
    int numCorrect = 0;

    for (int ei = 0; ei < numUniqueEvents; ei++) {
      for (int ni = 0; ni < this.numTimesEventsSeen[ei]; ni++) {

        double[] modelDistribution = new double[numOutcomes];

        if (values != null)
          NaiveBayesModel.eval(contexts[ei], values[ei], modelDistribution, evalParams, false);
        else
          NaiveBayesModel.eval(contexts[ei], null, modelDistribution, evalParams, false);

        int max = ArrayMath.argmax(modelDistribution);
        if (max == outcomeList[ei])
          numCorrect++;
      }
    }
    double trainingAccuracy = (double) numCorrect / numEvents;
    display("Stats: (" + numCorrect + "/" + numEvents + ") " + trainingAccuracy + "\n");
    return trainingAccuracy;
  }

}
