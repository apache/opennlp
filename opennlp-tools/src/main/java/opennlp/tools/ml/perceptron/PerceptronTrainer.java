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

import java.io.IOException;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.EvalParameters;
import opennlp.tools.ml.model.MutableContext;
import opennlp.tools.util.TrainingParameters;

/**
 * Trains models using the perceptron algorithm.  Each outcome is represented as
 * a binary perceptron classifier.  This supports standard (integer) weighting as well
 * average weighting as described in:
 * Discriminative Training Methods for Hidden Markov Models: Theory and Experiments
 * with the Perceptron Algorithm. Michael Collins, EMNLP 2002.
 *
 */
public class PerceptronTrainer extends AbstractEventTrainer {

  public static final String PERCEPTRON_VALUE = "PERCEPTRON";
  public static final double TOLERANCE_DEFAULT = .00001;

  /** Number of unique events which occurred in the event set. */
  private int numUniqueEvents;
  /** Number of events in the event set. */
  private int numEvents;

  /** Number of predicates. */
  private int numPreds;
  /** Number of outcomes. */
  private int numOutcomes;
  /** Records the array of predicates seen in each event. */
  private int[][] contexts;

  /** The value associates with each context. If null then context values are assumes to be 1. */
  private float[][] values;

  /** List of outcomes for each event i, in context[i]. */
  private int[] outcomeList;

  /** Records the num of times an event has been seen for each event i, in context[i]. */
  private int[] numTimesEventsSeen;

  /** Stores the String names of the outcomes.  The GIS only tracks outcomes
  as ints, and so this array is needed to save the model to disk and
  thereby allow users to know what the outcome was in human
  understandable terms. */
  private String[] outcomeLabels;

  /** Stores the String names of the predicates. The GIS only tracks
  predicates as ints, and so this array is needed to save the model to
  disk and thereby allow users to know what the outcome was in human
  understandable terms. */
  private String[] predLabels;

  private double tolerance = TOLERANCE_DEFAULT;

  private Double stepSizeDecrease;

  private boolean useSkippedlAveraging;

  public PerceptronTrainer() {
  }

  public PerceptronTrainer(TrainingParameters parameters) {
    super(parameters);
  }
  
  public boolean isValid() {
    if (!super.isValid()) {
      return false;
    }

    String algorithmName = getAlgorithm();
    if (algorithmName != null) {
      return PERCEPTRON_VALUE.equals(algorithmName);
    }
    else {
      return true;
    }
  }

  public boolean isSortAndMerge() {
    return false;
  }

  public AbstractModel doTrain(DataIndexer indexer) throws IOException {
    if (!isValid()) {
      throw new IllegalArgumentException("trainParams are not valid!");
    }

    int iterations = getIterations();
    int cutoff = getCutoff();

    AbstractModel model;

    boolean useAverage = trainingParameters.getBooleanParameter("UseAverage", true);

    boolean useSkippedAveraging = trainingParameters.getBooleanParameter("UseSkippedAveraging", false);

    // overwrite otherwise it might not work
    if (useSkippedAveraging)
      useAverage = true;

    double stepSizeDecrease = trainingParameters.getDoubleParameter("StepSizeDecrease", 0);

    double tolerance = trainingParameters.getDoubleParameter("Tolerance",
        PerceptronTrainer.TOLERANCE_DEFAULT);

    this.setSkippedAveraging(useSkippedAveraging);

    if (stepSizeDecrease > 0)
      this.setStepSizeDecrease(stepSizeDecrease);

    this.setTolerance(tolerance);

    model = this.trainModel(iterations, indexer, cutoff, useAverage);

    return model;
  }

  // << members related to AbstractSequenceTrainer

  /**
   * Specifies the tolerance. If the change in training set accuracy
   * is less than this, stop iterating.
   *
   * @param tolerance
   */
  public void setTolerance(double tolerance) {

    if (tolerance < 0) {
      throw new
          IllegalArgumentException("tolerance must be a positive number but is " + tolerance + "!");
    }

    this.tolerance = tolerance;
  }

  /**
   * Enables and sets step size decrease. The step size is
   * decreased every iteration by the specified value.
   *
   * @param decrease - step size decrease in percent
   */
  public void setStepSizeDecrease(double decrease) {

    if (decrease < 0 || decrease > 100) {
      throw new
          IllegalArgumentException("decrease must be between 0 and 100 but is " + decrease + "!");
    }

    stepSizeDecrease = decrease;
  }

  /**
   * Enables skipped averaging, this flag changes the standard
   * averaging to special averaging instead.
   * <p>
   * If we are doing averaging, and the current iteration is one
   * of the first 20 or it is a perfect square, then updated the
   * summed parameters.
   * <p>
   * The reason we don't take all of them is that the parameters change
   * less toward the end of training, so they drown out the contributions
   * of the more volatile early iterations. The use of perfect
   * squares allows us to sample from successively farther apart iterations.
   *
   * @param averaging averaging flag
   */
  public void setSkippedAveraging(boolean averaging) {
    useSkippedlAveraging = averaging;
  }

  public AbstractModel trainModel(int iterations, DataIndexer di, int cutoff) {
    return trainModel(iterations,di,cutoff,true);
  }

  public AbstractModel trainModel(int iterations, DataIndexer di, int cutoff, boolean useAverage) {
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

    MutableContext[] finalParameters = findParameters(iterations, useAverage);

    display("...done.\n");

    /* Create and return the model *************/
    return new PerceptronModel(finalParameters, predLabels, outcomeLabels);
  }

  private MutableContext[] findParameters(int iterations, boolean useAverage) {

    display("Performing " + iterations + " iterations.\n");

    int[] allOutcomesPattern = new int[numOutcomes];
    for (int oi = 0; oi < numOutcomes; oi++)
      allOutcomesPattern[oi] = oi;

    /* Stores the estimated parameter value of each predicate during iteration. */
    MutableContext[] params = new MutableContext[numPreds];
    for (int pi = 0; pi < numPreds; pi++) {
      params[pi] = new MutableContext(allOutcomesPattern,new double[numOutcomes]);
      for (int aoi = 0; aoi < numOutcomes; aoi++)
        params[pi].setParameter(aoi, 0.0);
    }

    EvalParameters evalParams = new EvalParameters(params, numOutcomes);

    /* Stores the sum of parameter values of each predicate over many iterations. */
    MutableContext[] summedParams = new MutableContext[numPreds];
    if (useAverage) {
      for (int pi = 0; pi < numPreds; pi++) {
        summedParams[pi] = new MutableContext(allOutcomesPattern,new double[numOutcomes]);
        for (int aoi = 0; aoi < numOutcomes; aoi++)
          summedParams[pi].setParameter(aoi, 0.0);
      }
    }

    // Keep track of the previous three accuracies. The difference of
    // the mean of these and the current training set accuracy is used
    // with tolerance to decide whether to stop.
    double prevAccuracy1 = 0.0;
    double prevAccuracy2 = 0.0;
    double prevAccuracy3 = 0.0;

    // A counter for the denominator for averaging.
    int numTimesSummed = 0;

    double stepsize = 1;
    for (int i = 1; i <= iterations; i++) {

      // Decrease the stepsize by a small amount.
      if (stepSizeDecrease != null)
        stepsize *= 1 - stepSizeDecrease;

      displayIteration(i);

      int numCorrect = 0;

      for (int ei = 0; ei < numUniqueEvents; ei++) {
        int targetOutcome = outcomeList[ei];

        for (int ni = 0; ni < this.numTimesEventsSeen[ei]; ni++) {

          // Compute the model's prediction according to the current parameters.
          double[] modelDistribution = new double[numOutcomes];
          if (values != null)
            PerceptronModel.eval(contexts[ei], values[ei], modelDistribution, evalParams, false);
          else
            PerceptronModel.eval(contexts[ei], null, modelDistribution, evalParams, false);

          int maxOutcome = maxIndex(modelDistribution);

          // If the predicted outcome is different from the target
          // outcome, do the standard update: boost the parameters
          // associated with the target and reduce those associated
          // with the incorrect predicted outcome.
          if (maxOutcome != targetOutcome) {
            for (int ci = 0; ci < contexts[ei].length; ci++) {
              int pi = contexts[ei][ci];
              if (values == null) {
                params[pi].updateParameter(targetOutcome, stepsize);
                params[pi].updateParameter(maxOutcome, -stepsize);
              } else {
                params[pi].updateParameter(targetOutcome, stepsize * values[ei][ci]);
                params[pi].updateParameter(maxOutcome, -stepsize * values[ei][ci]);
              }
            }
          }

          // Update the counts for accuracy.
          if (maxOutcome == targetOutcome)
            numCorrect++;
        }
      }

      // Calculate the training accuracy and display.
      double trainingAccuracy = (double) numCorrect / numEvents;
      if (i < 10 || (i % 10) == 0)
        display(". (" + numCorrect + "/" + numEvents + ") " + trainingAccuracy + "\n");

      // TODO: Make averaging configurable !!!

      boolean doAveraging;

      doAveraging = useAverage && useSkippedlAveraging && (i < 20 || isPerfectSquare(i)) || useAverage;

      if (doAveraging) {
        numTimesSummed++;
        for (int pi = 0; pi < numPreds; pi++)
          for (int aoi = 0; aoi < numOutcomes; aoi++)
            summedParams[pi].updateParameter(aoi, params[pi].getParameters()[aoi]);
      }

      // If the tolerance is greater than the difference between the
      // current training accuracy and all of the previous three
      // training accuracies, stop training.
      if (Math.abs(prevAccuracy1 - trainingAccuracy) < tolerance
          && Math.abs(prevAccuracy2 - trainingAccuracy) < tolerance
          && Math.abs(prevAccuracy3 - trainingAccuracy) < tolerance) {
        display("Stopping: change in training set accuracy less than " + tolerance + "\n");
        break;
      }

      // Update the previous training accuracies.
      prevAccuracy1 = prevAccuracy2;
      prevAccuracy2 = prevAccuracy3;
      prevAccuracy3 = trainingAccuracy;
    }

    // Output the final training stats.
    trainingStats(evalParams);

    // Create averaged parameters
    if (useAverage) {
      for (int pi = 0; pi < numPreds; pi++)
        for (int aoi = 0; aoi < numOutcomes; aoi++)
          summedParams[pi].setParameter(aoi, summedParams[pi].getParameters()[aoi] / numTimesSummed);

      return summedParams;

    } else {

      return params;

    }

  }

  private double trainingStats(EvalParameters evalParams) {
    int numCorrect = 0;

    for (int ei = 0; ei < numUniqueEvents; ei++) {
      for (int ni = 0; ni < this.numTimesEventsSeen[ei]; ni++) {

        double[] modelDistribution = new double[numOutcomes];

        if (values != null)
          PerceptronModel.eval(contexts[ei], values[ei], modelDistribution, evalParams,false);
        else
          PerceptronModel.eval(contexts[ei], null, modelDistribution, evalParams, false);

        int max = maxIndex(modelDistribution);
        if (max == outcomeList[ei])
          numCorrect++;
      }
    }
    double trainingAccuracy = (double) numCorrect / numEvents;
    display("Stats: (" + numCorrect + "/" + numEvents + ") " + trainingAccuracy + "\n");
    return trainingAccuracy;
  }


  private int maxIndex(double[] values) {
    int max = 0;
    for (int i = 1; i < values.length; i++)
      if (values[i] > values[max])
        max = i;
    return max;
  }

  private void displayIteration(int i) {
    if (i > 10 && (i % 10) != 0)
      return;

    if (i < 10)
      display("  " + i + ":  ");
    else if (i < 100)
      display(" " + i + ":  ");
    else
      display(i + ":  ");
  }

  // See whether a number is a perfect square. Inefficient, but fine
  // for our purposes.
  private static boolean isPerfectSquare(int n) {
    int root = (int) Math.sqrt(n);
    return root * root == n;
  }

}
