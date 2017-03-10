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

import java.io.IOException;

import opennlp.tools.ml.AbstractEventTrainer;
import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.Prior;
import opennlp.tools.ml.model.UniformPrior;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * A Factory class which uses instances of GISTrainer to create and train
 * GISModels.
 * @deprecated use {@link GISTrainer}
 */
@Deprecated
public class GIS extends AbstractEventTrainer {

  public static final String MAXENT_VALUE = "MAXENT";

  /**
   * Set this to false if you don't want messages about the progress of model
   * training displayed. Alternately, you can use the overloaded version of
   * trainModel() to conditionally enable progress messages.
   */
  public static boolean PRINT_MESSAGES = true;

  /**
   * If we are using smoothing, this is used as the "number" of times we want
   * the trainer to imagine that it saw a feature that it actually didn't see.
   * Defaulted to 0.1.
   */
  private static final double SMOOTHING_OBSERVATION = 0.1;

  private static final String SMOOTHING_PARAM = "smoothing";
  private static final boolean SMOOTHING_DEFAULT = false;

  public GIS() {
  }

  public GIS(TrainingParameters parameters) {
    super(parameters);
  }
  
  public boolean isValid() {

    if (!super.isValid()) {
      return false;
    }

    String algorithmName = getAlgorithm();

    return !(algorithmName != null && !(MAXENT_VALUE.equals(algorithmName)));
  }

  public boolean isSortAndMerge() {
    return true;
  }

  public AbstractModel doTrain(DataIndexer indexer) throws IOException {
    int iterations = getIterations();

    AbstractModel model;

    boolean printMessages = trainingParameters.getBooleanParameter(VERBOSE_PARAM, VERBOSE_DEFAULT);
    boolean smoothing = trainingParameters.getBooleanParameter(SMOOTHING_PARAM, SMOOTHING_DEFAULT);
    int threads = trainingParameters.getIntParameter(TrainingParameters.THREADS_PARAM, 1);

    model = trainModel(iterations, indexer, printMessages, smoothing, null, threads);

    return model;
  }

  // << members related to AbstractEventTrainer

  /**
   * Train a model using the GIS algorithm, assuming 100 iterations and no
   * cutoff.
   *
   * @param eventStream
   *          The EventStream holding the data on which this model will be
   *          trained.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.tools.ml.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(ObjectStream<Event> eventStream) throws IOException {
    return trainModel(eventStream, 100, 0, false, PRINT_MESSAGES);
  }

  /**
   * Train a model using the GIS algorithm, assuming 100 iterations and no
   * cutoff.
   *
   * @param eventStream
   *          The EventStream holding the data on which this model will be
   *          trained.
   * @param smoothing
   *          Defines whether the created trainer will use smoothing while
   *          training the model.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.tools.ml.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(ObjectStream<Event> eventStream, boolean smoothing)
      throws IOException {
    return trainModel(eventStream, 100, 0, smoothing, PRINT_MESSAGES);
  }

  /**
   * Train a model using the GIS algorithm.
   *
   * @param eventStream
   *          The EventStream holding the data on which this model will be
   *          trained.
   * @param iterations
   *          The number of GIS iterations to perform.
   * @param cutoff
   *          The number of times a feature must be seen in order to be relevant
   *          for training.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.tools.ml.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(ObjectStream<Event> eventStream, int iterations,
      int cutoff) throws IOException {
    return trainModel(eventStream, iterations, cutoff, false, PRINT_MESSAGES);
  }

  /**
   * Train a model using the GIS algorithm.
   *
   * @param eventStream
   *          The EventStream holding the data on which this model will be
   *          trained.
   * @param iterations
   *          The number of GIS iterations to perform.
   * @param cutoff
   *          The number of times a feature must be seen in order to be relevant
   *          for training.
   * @param smoothing
   *          Defines whether the created trainer will use smoothing while
   *          training the model.
   * @param printMessagesWhileTraining
   *          Determines whether training status messages are written to STDOUT.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.tools.ml.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(ObjectStream<Event> eventStream, int iterations,
      int cutoff, boolean smoothing, boolean printMessagesWhileTraining)
      throws IOException {
    GISTrainer trainer = new GISTrainer(printMessagesWhileTraining);
    trainer.setSmoothing(smoothing);
    trainer.setSmoothingObservation(SMOOTHING_OBSERVATION);
    return trainer.trainModel(eventStream, iterations, cutoff);
  }

  /**
   * Train a model using the GIS algorithm.
   *
   * @param eventStream
   *          The EventStream holding the data on which this model will be
   *          trained.
   * @param iterations
   *          The number of GIS iterations to perform.
   * @param cutoff
   *          The number of times a feature must be seen in order to be relevant
   *          for training.
   * @param sigma
   *          The standard deviation for the gaussian smoother.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.tools.ml.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(ObjectStream<Event> eventStream, int iterations,
      int cutoff, double sigma) throws IOException {
    GISTrainer trainer = new GISTrainer(PRINT_MESSAGES);
    if (sigma > 0) {
      trainer.setGaussianSigma(sigma);
    }
    return trainer.trainModel(eventStream, iterations, cutoff);
  }

  /**
   * Train a model using the GIS algorithm.
   *
   * @param iterations
   *          The number of GIS iterations to perform.
   * @param indexer
   *          The object which will be used for event compilation.
   * @param smoothing
   *          Defines whether the created trainer will use smoothing while
   *          training the model.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.tools.ml.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(int iterations, DataIndexer indexer, boolean smoothing) {
    return trainModel(iterations, indexer, true, smoothing, null, 1);
  }

  /**
   * Train a model using the GIS algorithm.
   *
   * @param iterations
   *          The number of GIS iterations to perform.
   * @param indexer
   *          The object which will be used for event compilation.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.tools.ml.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(int iterations, DataIndexer indexer) {
    return trainModel(iterations, indexer, true, false, null, 1);
  }

  /**
   * Train a model using the GIS algorithm with the specified number of
   * iterations, data indexer, and prior.
   *
   * @param iterations
   *          The number of GIS iterations to perform.
   * @param indexer
   *          The object which will be used for event compilation.
   * @param modelPrior
   *          The prior distribution for the model.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.tools.ml.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(int iterations, DataIndexer indexer,
      Prior modelPrior, int cutoff) {
    return trainModel(iterations, indexer, true, false, modelPrior, cutoff);
  }

  /**
   * Train a model using the GIS algorithm.
   *
   * @param iterations
   *          The number of GIS iterations to perform.
   * @param indexer
   *          The object which will be used for event compilation.
   * @param printMessagesWhileTraining
   *          Determines whether training status messages are written to STDOUT.
   * @param smoothing
   *          Defines whether the created trainer will use smoothing while
   *          training the model.
   * @param modelPrior
   *          The prior distribution for the model.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.tools.ml.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(int iterations, DataIndexer indexer,
                                    boolean printMessagesWhileTraining, boolean smoothing,
                                    Prior modelPrior) {
    return trainModel(iterations, indexer, printMessagesWhileTraining, smoothing, modelPrior, 1);
  }

  /**
   * Train a model using the GIS algorithm.
   *
   * @param iterations
   *          The number of GIS iterations to perform.
   * @param indexer
   *          The object which will be used for event compilation.
   * @param printMessagesWhileTraining
   *          Determines whether training status messages are written to STDOUT.
   * @param smoothing
   *          Defines whether the created trainer will use smoothing while
   *          training the model.
   * @param modelPrior
   *          The prior distribution for the model.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.tools.ml.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(int iterations, DataIndexer indexer,
                                    boolean printMessagesWhileTraining, boolean smoothing,
                                    Prior modelPrior, int threads) {
    GISTrainer trainer = new GISTrainer(printMessagesWhileTraining);
    trainer.setSmoothing(smoothing);
    trainer.setSmoothingObservation(SMOOTHING_OBSERVATION);
    if (modelPrior == null) {
      modelPrior = new UniformPrior();
    }
    return trainer.trainModel(iterations, indexer, modelPrior, threads);
  }
}



