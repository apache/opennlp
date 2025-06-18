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

package opennlp.tools.ml;

import java.lang.reflect.Constructor;
import java.util.Map;

import opennlp.tools.commons.Trainer;
import opennlp.tools.ml.maxent.GISTrainer;
import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
import opennlp.tools.ml.naivebayes.NaiveBayesTrainer;
import opennlp.tools.ml.perceptron.PerceptronTrainer;
import opennlp.tools.ml.perceptron.SimplePerceptronSequenceTrainer;
import opennlp.tools.monitoring.DefaultTrainingProgressMonitor;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingConfiguration;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.ext.ExtensionNotLoadedException;

/**
 * A factory to initialize {@link Trainer} instances depending on a trainer type
 * configured via {@link Parameters}.
 */
public class TrainerFactory {

  public enum TrainerType {
    EVENT_MODEL_TRAINER,
    EVENT_MODEL_SEQUENCE_TRAINER,
    SEQUENCE_TRAINER
  }

  // built-in trainers
  private static final Map<String, Class<? extends Trainer<TrainingParameters>>> BUILTIN_TRAINERS;

  /*
   * Initialize the built-in trainers
   */
  static {
    BUILTIN_TRAINERS = Map.of(
        GISTrainer.MAXENT_VALUE, GISTrainer.class,
        QNTrainer.MAXENT_QN_VALUE, QNTrainer.class,
        PerceptronTrainer.PERCEPTRON_VALUE, PerceptronTrainer.class,
        SimplePerceptronSequenceTrainer.PERCEPTRON_SEQUENCE_VALUE, SimplePerceptronSequenceTrainer.class,
        NaiveBayesTrainer.NAIVE_BAYES_VALUE, NaiveBayesTrainer.class);
  }

  /**
   * Determines the {@link TrainerType} based on the
   * {@link Parameters#ALGORITHM_PARAM} value.
   *
   * @param trainParams - A mapping of {@link Parameters training parameters}.
   * @return The {@link TrainerType} or {@code null} if the type couldn't be determined.
   */
  public static TrainerType getTrainerType(Parameters trainParams) {

    String algorithmValue = trainParams.getStringParameter(Parameters.ALGORITHM_PARAM, null);

    // Check if it is defaulting to the MAXENT trainer
    if (algorithmValue == null) {
      return TrainerType.EVENT_MODEL_TRAINER;
    }

    Class<? extends Trainer<TrainingParameters>> trainerClass = BUILTIN_TRAINERS.get(algorithmValue);

    if (trainerClass != null) {

      if (EventTrainer.class.isAssignableFrom(trainerClass)) {
        return TrainerType.EVENT_MODEL_TRAINER;
      } else if (EventModelSequenceTrainer.class.isAssignableFrom(trainerClass)) {
        return TrainerType.EVENT_MODEL_SEQUENCE_TRAINER;
      } else if (SequenceTrainer.class.isAssignableFrom(trainerClass)) {
        return TrainerType.SEQUENCE_TRAINER;
      }
    }

    // Try to load the different trainers, and return the type on success

    try {
      ExtensionLoader.instantiateExtension(EventTrainer.class, algorithmValue);
      return TrainerType.EVENT_MODEL_TRAINER;
    } catch (ExtensionNotLoadedException ignored) {
      // this is ignored
    }

    try {
      ExtensionLoader.instantiateExtension(EventModelSequenceTrainer.class, algorithmValue);
      return TrainerType.EVENT_MODEL_SEQUENCE_TRAINER;
    } catch (ExtensionNotLoadedException ignored) {
      // this is ignored
    }

    try {
      ExtensionLoader.instantiateExtension(SequenceTrainer.class, algorithmValue);
      return TrainerType.SEQUENCE_TRAINER;
    } catch (ExtensionNotLoadedException ignored) {
      // this is ignored
    }

    return null;
  }

  /**
   * Retrieves a {@link SequenceTrainer} that fits the given parameters.
   *
   * @param trainParams The {@link Parameters} to check for the trainer type.
   *                    Note: The entry {@link Parameters#ALGORITHM_PARAM} is used
   *                    to determine the type.
   * @param reportMap   A {@link Map} that shall be used during initialization of
   *                    the {@link SequenceTrainer}.
   * @return A valid {@link SequenceTrainer} for the configured {@code trainParams}.
   * @throws IllegalArgumentException Thrown if the trainer type could not be determined.
   */
  public static SequenceTrainer<TrainingParameters> getSequenceModelTrainer(
          TrainingParameters trainParams, Map<String, String> reportMap) {
    String trainerType = trainParams.getStringParameter(Parameters.ALGORITHM_PARAM, null);

    if (trainerType != null) {
      final SequenceTrainer<TrainingParameters> trainer;
      if (BUILTIN_TRAINERS.containsKey(trainerType)) {
        trainer = TrainerFactory.createBuiltinTrainer(BUILTIN_TRAINERS.get(trainerType));
      } else {
        trainer = ExtensionLoader.instantiateExtension(SequenceTrainer.class, trainerType);
      }
      trainer.init(trainParams, reportMap);
      return trainer;
    } else {
      throw new IllegalArgumentException("Trainer type couldn't be determined!");
    }
  }

  /**
   * Retrieves an {@link EventModelSequenceTrainer} that fits the given parameters.
   *
   * @param trainParams The {@link Parameters} to check for the trainer type.
   *                    Note: The entry {@link Parameters#ALGORITHM_PARAM} is used
   *                    to determine the type.
   * @param reportMap   A {@link Map} that shall be used during initialization of
   *                    the {@link EventModelSequenceTrainer}.
   * @return A valid {@link EventModelSequenceTrainer} for the configured {@code trainParams}.
   * @throws IllegalArgumentException Thrown if the trainer type could not be determined.
   */
  public static <T> EventModelSequenceTrainer<T, TrainingParameters> getEventModelSequenceTrainer(
          TrainingParameters trainParams, Map<String, String> reportMap) {
    String trainerType = trainParams.getStringParameter(Parameters.ALGORITHM_PARAM, null);

    if (trainerType != null) {
      final EventModelSequenceTrainer<T, TrainingParameters> trainer;
      if (BUILTIN_TRAINERS.containsKey(trainerType)) {
        trainer = TrainerFactory.createBuiltinTrainer(BUILTIN_TRAINERS.get(trainerType));
      } else {
        trainer = ExtensionLoader.instantiateExtension(EventModelSequenceTrainer.class, trainerType);
      }
      trainer.init(trainParams, reportMap);
      return trainer;
    } else {
      throw new IllegalArgumentException("Trainer type couldn't be determined!");
    }
  }

  /**
   * Works just like {@link TrainerFactory#getEventTrainer(TrainingParameters, Map, TrainingConfiguration)}
   * except that {@link TrainingConfiguration} is initialized with default values.
   */
  public static EventTrainer<TrainingParameters> getEventTrainer(
          TrainingParameters trainParams, Map<String, String> reportMap) {

    TrainingConfiguration trainingConfiguration
        = new TrainingConfiguration(new DefaultTrainingProgressMonitor(), null);
    return getEventTrainer(trainParams, reportMap, trainingConfiguration);
  }

  /**
   * Retrieves an {@link EventTrainer} that fits the given parameters.
   *
   * @param trainParams The {@link Parameters} to check for the trainer type.
   *                    Note: The entry {@link Parameters#ALGORITHM_PARAM} is used
   *                    to determine the type. If the type is not defined, the
   *                    {@link GISTrainer#MAXENT_VALUE} will be used.
   * @param reportMap   A {@link Map} that shall be used during initialization of
   *                    the {@link EventTrainer}.
   * @param config      The {@link TrainingConfiguration} to be used.
   * @return A valid {@link EventTrainer} for the configured {@code trainParams}.
   */
  public static EventTrainer<TrainingParameters> getEventTrainer(
          TrainingParameters trainParams, Map<String, String> reportMap, TrainingConfiguration config) {

    // if the trainerType is not defined -- use the GISTrainer.
    String trainerType = trainParams.getStringParameter(
        Parameters.ALGORITHM_PARAM, GISTrainer.MAXENT_VALUE);

    final EventTrainer<TrainingParameters> trainer;
    if (BUILTIN_TRAINERS.containsKey(trainerType)) {
      trainer = TrainerFactory.createBuiltinTrainer(BUILTIN_TRAINERS.get(trainerType));
    } else {
      trainer = ExtensionLoader.instantiateExtension(EventTrainer.class, trainerType);
    }
    trainer.init(trainParams, reportMap, config);
    return trainer;
  }

  /**
   * @param trainParams The {@link Parameters} to validate. Must not be {@code null}.
   * @return {@code true} if the {@code trainParams} could be validated, {@code false} otherwise.
   */
  public static boolean isValid(Parameters trainParams) {

    // TODO: Need to validate all parameters correctly ... error prone?!
    String algorithmName = trainParams.getStringParameter(Parameters.ALGORITHM_PARAM,
        null);

    // If a trainer type can be determined, then the trainer is valid!
    if (algorithmName != null &&
        !(BUILTIN_TRAINERS.containsKey(algorithmName) || getTrainerType(trainParams) != null)) {
      return false;
    }

    try {
      // require that the Cutoff and the number of iterations be an integer.
      // if they are not set, the default values will be ok.
      trainParams.getIntParameter(Parameters.CUTOFF_PARAM,
          Parameters.CUTOFF_DEFAULT_VALUE);
      trainParams.getIntParameter(Parameters.ITERATIONS_PARAM,
          Parameters.ITERATIONS_DEFAULT_VALUE);
    } catch (NumberFormatException e) {
      return false;
    }

    // no reason to require that the dataIndexer be a 1-pass or 2-pass data indexer.
    trainParams.getStringParameter(AbstractEventTrainer.DATA_INDEXER_PARAM, null);

    // TODO: Check data indexing ...
    return true;
  }

  @SuppressWarnings("unchecked")
  private static <P extends Parameters, T extends Trainer<P>> T createBuiltinTrainer(
          Class<? extends Trainer<P>> trainerClass) {
    Trainer<P> theTrainer = null;
    if (trainerClass != null) {
      try {
        Constructor<? extends Trainer<P>> c = trainerClass.getConstructor();
        theTrainer = c.newInstance();
      } catch (Exception e) {
        String msg = "Could not instantiate the " + trainerClass.getCanonicalName()
            + ". The initialization threw an exception.";
        throw new IllegalArgumentException(msg, e);
      }
    }
    return (T) theTrainer;
  }
}
