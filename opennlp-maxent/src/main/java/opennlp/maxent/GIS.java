/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.maxent;

import java.io.IOException;

import opennlp.model.DataIndexer;
import opennlp.model.EventStream;
import opennlp.model.Prior;
import opennlp.model.UniformPrior;

/**
 * A Factory class which uses instances of GISTrainer to create and train
 * GISModels.
 */
public class GIS {
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
  public static double SMOOTHING_OBSERVATION = 0.1;

  /**
   * Train a model using the GIS algorithm, assuming 100 iterations and no
   * cutoff.
   * 
   * @param eventStream
   *          The EventStream holding the data on which this model will be
   *          trained.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(EventStream eventStream) throws IOException {
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
   *         disk using an opennlp.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(EventStream eventStream, boolean smoothing)
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
   *         disk using an opennlp.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(EventStream eventStream, int iterations,
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
   *         disk using an opennlp.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(EventStream eventStream, int iterations,
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
   *         disk using an opennlp.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(EventStream eventStream, int iterations,
      int cutoff, double sigma) throws IOException {
    GISTrainer trainer = new GISTrainer(PRINT_MESSAGES);
    if (sigma > 0)
      trainer.setGaussianSigma(sigma);
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
   *         disk using an opennlp.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(int iterations, DataIndexer indexer,
      boolean smoothing) {
    return trainModel(iterations, indexer, true, smoothing, null, 0);
  }

  /**
   * Train a model using the GIS algorithm.
   * 
   * @param iterations
   *          The number of GIS iterations to perform.
   * @param indexer
   *          The object which will be used for event compilation.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(int iterations, DataIndexer indexer) {
    return trainModel(iterations, indexer, true, false, null, 0);
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
   *         disk using an opennlp.maxent.io.GISModelWriter object.
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
   * @param cutoff
   *          The number of times a predicate must occur to be used in a model.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(int iterations, DataIndexer indexer,
      boolean printMessagesWhileTraining, boolean smoothing, Prior modelPrior,
      int cutoff) {
    return trainModel(iterations, indexer, printMessagesWhileTraining,
        smoothing, modelPrior, cutoff, 1);
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
   * @param cutoff
   *          The number of times a predicate must occur to be used in a model.
   * @return The newly trained model, which can be used immediately or saved to
   *         disk using an opennlp.maxent.io.GISModelWriter object.
   */
  public static GISModel trainModel(int iterations, DataIndexer indexer,
      boolean printMessagesWhileTraining, boolean smoothing, Prior modelPrior,
      int cutoff, int threads) {
    GISTrainer trainer = new GISTrainer(printMessagesWhileTraining);
    trainer.setSmoothing(smoothing);
    trainer.setSmoothingObservation(SMOOTHING_OBSERVATION);
    if (modelPrior == null) {
      modelPrior = new UniformPrior();
    }
    
    return trainer.trainModel(iterations, indexer, modelPrior, cutoff, threads);
  }
}



