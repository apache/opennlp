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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.ml.AbstractEventModelSequenceTrainer;
import opennlp.tools.ml.model.AbstractDataIndexer;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MutableContext;
import opennlp.tools.ml.model.OnePassDataIndexer;
import opennlp.tools.ml.model.Sequence;
import opennlp.tools.ml.model.SequenceStream;
import opennlp.tools.ml.model.SequenceStreamEventStream;

/**
 * Trains {@link PerceptronModel models} with sequences using the perceptron algorithm.
 * <p>
 * Each outcome is represented as a binary perceptron classifier.
 * This supports standard (integer) weighting as well average weighting.
 * <p>
 * Sequence information is used in a simplified was to that described in:
 * Discriminative Training Methods for Hidden Markov Models: Theory and Experiments
 * with the Perceptron Algorithm. Michael Collins, EMNLP 2002.
 * <p>
 * Specifically only updates are applied to tokens which were incorrectly tagged by a sequence tagger
 * rather than to all feature across the sequence which differ from the training sequence.
 *
 * @see PerceptronModel
 * @see AbstractEventModelSequenceTrainer
 */
public class SimplePerceptronSequenceTrainer extends AbstractEventModelSequenceTrainer {

  private static final Logger logger = LoggerFactory.getLogger(SimplePerceptronSequenceTrainer.class);
  public static final String PERCEPTRON_SEQUENCE_VALUE = "PERCEPTRON_SEQUENCE";

  private int iterations;
  private SequenceStream<Event> sequenceStream;
  /**
   * Number of events in the event set.
   */
  private int numEvents;

  /**
   * Number of predicates.
   */
  private int numPreds;
  private int numOutcomes;

  /**
   * List of outcomes for each event i, in context[i].
   */
  private int[] outcomeList;

  private String[] outcomeLabels;

  /**
   * Stores the average parameter values of each predicate during iteration.
   */
  private MutableContext[] averageParams;

  /**
   * Mapping between context and an integer
   */
  private Map<String, Integer> pmap;

  private Map<String, Integer> omap;

  /**
   * Stores the estimated parameter value of each predicate during iteration.
   */
  private MutableContext[] params;
  private boolean useAverage;
  private int[][][] updates;
  private static final int VALUE = 0;
  private static final int ITER = 1;
  private static final int EVENT = 2;

  private String[] predLabels;
  private int numSequences;

  /**
   * Instantiates a {@link SimplePerceptronSequenceTrainer} with a default
   * configuration of training parameters.
   */
  public SimplePerceptronSequenceTrainer() {
  }

  /**
   * {@inheritDoc}
   *
   * @throws IllegalArgumentException Thrown if the algorithm name is not equal to
   *                                  {{@link #PERCEPTRON_SEQUENCE_VALUE}}.
   */
  @Override
  public void validate() {
    super.validate();

    String algorithmName = getAlgorithm();
    if (algorithmName != null) {
      if (!PERCEPTRON_SEQUENCE_VALUE.equals(algorithmName)) {
        throw new IllegalArgumentException("algorithmName must be PERCEPTRON_SEQUENCE");
      }
    }
  }

  @Override
  public AbstractModel doTrain(SequenceStream<Event> events) throws IOException {
    int iterations = getIterations();
    int cutoff = getCutoff();

    boolean useAverage = trainingParameters.getBooleanParameter("UseAverage", true);

    return trainModel(iterations, events, cutoff, useAverage);
  }

  // << members related to AbstractSequenceTrainer

  /**
   * Trains a {@link PerceptronModel} with given parameters.
   *
   * @param iterations     The number of iterations to use for training.
   * @param sequenceStream The {@link SequenceStream<Event>} used as data input.
   * @param cutoff         The {{@link #CUTOFF_PARAM}} value to use for training.
   * @param useAverage     Whether to use 'averaging', or not.
   * @return A valid, trained {@link AbstractModel perceptron model}.
   */
  public AbstractModel trainModel(int iterations, SequenceStream<Event> sequenceStream,
                                  int cutoff, boolean useAverage) throws IOException {
    this.iterations = iterations;
    this.sequenceStream = sequenceStream;

    trainingParameters.put(AbstractDataIndexer.CUTOFF_PARAM, cutoff);
    trainingParameters.put(AbstractDataIndexer.SORT_PARAM, false);
    DataIndexer di = new OnePassDataIndexer();
    di.init(trainingParameters, reportMap);
    di.index(new SequenceStreamEventStream(sequenceStream));
    numSequences = 0;

    sequenceStream.reset();

    while (sequenceStream.read() != null) {
      numSequences++;
    }

    outcomeList = di.getOutcomeList();
    predLabels = di.getPredLabels();
    pmap = new HashMap<>();

    for (int i = 0; i < predLabels.length; i++) {
      pmap.put(predLabels[i], i);
    }

    logger.info("Incorporating indexed data for training... ");
    this.useAverage = useAverage;
    numEvents = di.getNumEvents();

    this.iterations = iterations;
    outcomeLabels = di.getOutcomeLabels();
    omap = new HashMap<>();
    for (int oli = 0; oli < outcomeLabels.length; oli++) {
      omap.put(outcomeLabels[oli], oli);
    }
    outcomeList = di.getOutcomeList();

    numPreds = predLabels.length;
    numOutcomes = outcomeLabels.length;
    if (useAverage) {
      updates = new int[numPreds][numOutcomes][3];
    }

    logger.info("done.");

    logger.info("\tNumber of Event Tokens: {} " +
        "\n\t Number of Outcomes: {} " +
        "\n\t Number of Predicates: {}", numEvents, numOutcomes, numPreds);

    params = new MutableContext[numPreds];
    if (useAverage) {
      averageParams = new MutableContext[numPreds];
    }

    int[] allOutcomesPattern = new int[numOutcomes];
    for (int oi = 0; oi < numOutcomes; oi++) {
      allOutcomesPattern[oi] = oi;
    }

    for (int pi = 0; pi < numPreds; pi++) {
      params[pi] = new MutableContext(allOutcomesPattern, new double[numOutcomes]);
      if (useAverage) {
        averageParams[pi] = new MutableContext(allOutcomesPattern, new double[numOutcomes]);
      }
      for (int aoi = 0; aoi < numOutcomes; aoi++) {
        params[pi].setParameter(aoi, 0.0);
        if (useAverage) {
          averageParams[pi].setParameter(aoi, 0.0);
        }
      }
    }

    logger.info("Computing model parameters...");
    findParameters(iterations);
    logger.info("...done.");

    /* Create and return the model ****/
    String[] updatedPredLabels = predLabels;

    if (useAverage) {
      return new PerceptronModel(averageParams, updatedPredLabels, outcomeLabels);
    } else {
      return new PerceptronModel(params, updatedPredLabels, outcomeLabels);
    }
  }

  private void findParameters(int iterations) throws IOException {
    logger.info("Performing {} iterations.\n", iterations);
    for (int i = 1; i <= iterations; i++) {
      nextIteration(i);
    }
    if (useAverage) {
      trainingStats(averageParams);
    } else {
      trainingStats(params);
    }
  }

  public void nextIteration(int iteration) throws IOException {
    iteration--; //move to 0-based index
    int numCorrect = 0;
    int oei = 0;
    int si = 0;
    List<Map<String, Float>> featureCounts = new ArrayList<>(numOutcomes);
    for (int oi = 0; oi < numOutcomes; oi++) {
      featureCounts.add(new HashMap<>());
    }
    PerceptronModel model = new PerceptronModel(params, predLabels, outcomeLabels);

    sequenceStream.reset();

    Sequence<Event> sequence;
    while ((sequence = sequenceStream.read()) != null) {
      Event[] taggerEvents = sequenceStream.updateContext(sequence, model);
      Event[] events = sequence.getEvents();
      boolean update = false;
      for (int ei = 0; ei < events.length; ei++, oei++) {
        if (!taggerEvents[ei].getOutcome().equals(events[ei].getOutcome())) {
          update = true;
          //break;
        } else {
          numCorrect++;
        }
      }
      if (update) {
        for (int oi = 0; oi < numOutcomes; oi++) {
          featureCounts.get(oi).clear();
        }
        if (logger.isTraceEnabled()) {
          final StringBuilder sb = new StringBuilder();
          for (Event event : events) {
            sb.append(" ").append(event.getOutcome());
          }
          logger.trace("train: {}", sb);
        }

        //training feature count computation
        for (int ei = 0; ei < events.length; ei++, oei++) {
          String[] contextStrings = events[ei].getContext();
          float[] values = events[ei].getValues();
          int oi = omap.get(events[ei].getOutcome());
          for (int ci = 0; ci < contextStrings.length; ci++) {
            float value = 1;
            if (values != null) {
              value = values[ci];
            }
            Float c = featureCounts.get(oi).get(contextStrings[ci]);
            if (c == null) {
              c = value;
            } else {
              c += value;
            }
            featureCounts.get(oi).put(contextStrings[ci], c);
          }
        }
        //evaluation feature count computation
        if (logger.isTraceEnabled()) {
          final StringBuilder sb = new StringBuilder();
          for (Event taggerEvent : taggerEvents) {
            sb.append(" ").append(taggerEvent.getOutcome());
          }
          logger.trace("test: {}", sb);
        }
        for (Event taggerEvent : taggerEvents) {
          String[] contextStrings = taggerEvent.getContext();
          float[] values = taggerEvent.getValues();
          int oi = omap.get(taggerEvent.getOutcome());
          for (int ci = 0; ci < contextStrings.length; ci++) {
            float value = 1;
            if (values != null) {
              value = values[ci];
            }
            Float c = featureCounts.get(oi).get(contextStrings[ci]);
            if (c == null) {
              c = -1 * value;
            } else {
              c -= value;
            }
            if (c == 0f) {
              featureCounts.get(oi).remove(contextStrings[ci]);
            } else {
              featureCounts.get(oi).put(contextStrings[ci], c);
            }
          }
        }
        for (int oi = 0; oi < numOutcomes; oi++) {
          for (String feature : featureCounts.get(oi).keySet()) {
            int pi = pmap.getOrDefault(feature, -1);
            if (pi != -1) {
              if (logger.isTraceEnabled()) {
                logger.trace("{} {} {} {}",
                    si, outcomeLabels[oi], feature, featureCounts.get(oi).get(feature));
              }
              params[pi].updateParameter(oi, featureCounts.get(oi).get(feature));
              if (useAverage) {
                if (updates[pi][oi][VALUE] != 0) {
                  averageParams[pi].updateParameter(oi, updates[pi][oi][VALUE] * (numSequences
                      * (iteration - updates[pi][oi][ITER]) + (si - updates[pi][oi][EVENT])));
                  if (logger.isTraceEnabled()) {
                    logger.trace("p avp[{}].{}={}", pi, oi, averageParams[pi].getParameters()[oi]);
                  }
                }
                if (logger.isTraceEnabled()) {
                  logger.trace("p updates[{}]{{}]=({},{},{})({},{},{}) -> {}", pi, oi, updates[pi][oi][ITER],
                      updates[pi][oi][EVENT], updates[pi][oi][VALUE], iteration, oei,
                      params[pi].getParameters()[oi], averageParams[pi].getParameters()[oi]);
                }
                updates[pi][oi][VALUE] = (int) params[pi].getParameters()[oi];
                updates[pi][oi][ITER] = iteration;
                updates[pi][oi][EVENT] = si;
              }
            }
          }
        }
        model = new PerceptronModel(params, predLabels, outcomeLabels);
      }
      si++;
    }
    //finish average computation
    double totIterations = (double) iterations * si;
    if (useAverage && iteration == iterations - 1) {
      for (int pi = 0; pi < numPreds; pi++) {
        double[] predParams = averageParams[pi].getParameters();
        for (int oi = 0; oi < numOutcomes; oi++) {
          if (updates[pi][oi][VALUE] != 0) {
            predParams[oi] += updates[pi][oi][VALUE] * (numSequences
                * (iterations - updates[pi][oi][ITER]) - updates[pi][oi][EVENT]);
          }
          if (predParams[oi] != 0) {
            predParams[oi] /= totIterations;
            averageParams[pi].setParameter(oi, predParams[oi]);
            if (logger.isTraceEnabled()) {
              logger.trace("updates[{}][{}]=({},{},{})({},{},{}) -> {}", pi, oi, updates[pi][oi][ITER],
                  updates[pi][oi][EVENT], updates[pi][oi][VALUE], iterations, 0,
                  params[pi].getParameters()[oi], averageParams[pi].getParameters()[oi]);
            }
          }
        }
      }
    }
    logger.info("{}. ({}/{}) {}", iteration, numCorrect,
        numEvents, ((double) numCorrect / numEvents));
  }

  private void trainingStats(MutableContext[] params) throws IOException {
    int numCorrect = 0;
    int oei = 0;

    sequenceStream.reset();

    Sequence<Event> sequence;
    while ((sequence = sequenceStream.read()) != null) {
      Event[] taggerEvents = sequenceStream.updateContext(sequence,
          new PerceptronModel(params, predLabels, outcomeLabels));
      for (int ei = 0; ei < taggerEvents.length; ei++, oei++) {
        int max = omap.get(taggerEvents[ei].getOutcome());
        if (max == outcomeList[oei]) {
          numCorrect++;
        }
      }
    }
    logger.info(". ({}/{}) {}", numCorrect, numEvents, ((double) numCorrect / numEvents));
  }
}
