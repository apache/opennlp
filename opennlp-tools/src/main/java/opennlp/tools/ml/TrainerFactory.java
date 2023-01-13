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
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.ext.ExtensionNotLoadedException;

/**
 * A factory to initialize {@link Trainer} instances depending on a trainer type
 * configured via {@link TrainingParameters}.
 */
public class TrainerFactory {

  public enum TrainerType {
    EVENT_MODEL_TRAINER,
    EVENT_MODEL_SEQUENCE_TRAINER,
    SEQUENCE_TRAINER
  }

  // built-in trainers
  private static final Map<String, Class<? extends Trainer>> BUILTIN_TRAINERS;

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
   * {@link AbstractTrainer#ALGORITHM_PARAM} value.
   *
   * @param trainParams - A mapping of {@link TrainingParameters training parameters}.
   *
   * @return The {@link TrainerType} or {@code null} if the type couldn't be determined.
   */
  public static TrainerType getTrainerType(TrainingParameters trainParams) {

    String algorithmValue = trainParams.getStringParameter(AbstractTrainer.ALGORITHM_PARAM,null);

    // Check if it is defaulting to the MAXENT trainer
    if (algorithmValue == null) {
      return TrainerType.EVENT_MODEL_TRAINER;
    }

    Class<? extends Trainer> trainerClass = BUILTIN_TRAINERS.get(algorithmValue);

    if (trainerClass != null) {

      if (EventTrainer.class.isAssignableFrom(trainerClass)) {
        return TrainerType.EVENT_MODEL_TRAINER;
      }
      else if (EventModelSequenceTrainer.class.isAssignableFrom(trainerClass)) {
        return TrainerType.EVENT_MODEL_SEQUENCE_TRAINER;
      }
      else if (SequenceTrainer.class.isAssignableFrom(trainerClass)) {
        return TrainerType.SEQUENCE_TRAINER;
      }
    }

    // Try to load the different trainers, and return the type on success

    try {
      ExtensionLoader.instantiateExtension(EventTrainer.class, algorithmValue);
      return TrainerType.EVENT_MODEL_TRAINER;
    }
    catch (ExtensionNotLoadedException ignored) {
      // this is ignored
    }

    try {
      ExtensionLoader.instantiateExtension(EventModelSequenceTrainer.class, algorithmValue);
      return TrainerType.EVENT_MODEL_SEQUENCE_TRAINER;
    }
    catch (ExtensionNotLoadedException ignored) {
      // this is ignored
    }

    try {
      ExtensionLoader.instantiateExtension(SequenceTrainer.class, algorithmValue);
      return TrainerType.SEQUENCE_TRAINER;
    }
    catch (ExtensionNotLoadedException ignored) {
      // this is ignored
    }

    return null;
  }

  /**
   * Retrieves a {@link SequenceTrainer} that fits the given parameters.
   *
   * @param trainParams The {@link TrainingParameters} to check for the trainer type.
   *                    Note: The entry {@link AbstractTrainer#ALGORITHM_PARAM} is used
   *                    to determine the type.
   * @param reportMap A {@link Map} that shall be used during initialization of
   *                  the {@link SequenceTrainer}.
   *                  
   * @return A valid {@link SequenceTrainer} for the configured {@code trainParams}.
   * @throws IllegalArgumentException Thrown if the trainer type could not be determined.
   */
  public static SequenceTrainer getSequenceModelTrainer(
          TrainingParameters trainParams, Map<String, String> reportMap) {
    String trainerType = trainParams.getStringParameter(AbstractTrainer.ALGORITHM_PARAM,null);

    if (trainerType != null) {
      final SequenceTrainer trainer;
      if (BUILTIN_TRAINERS.containsKey(trainerType)) {
        trainer = TrainerFactory.createBuiltinTrainer(BUILTIN_TRAINERS.get(trainerType));
      } else {
        trainer = ExtensionLoader.instantiateExtension(SequenceTrainer.class, trainerType);
      }
      trainer.init(trainParams, reportMap);
      return trainer;
    }
    else {
      throw new IllegalArgumentException("Trainer type couldn't be determined!");
    }
  }

  /**
   * Retrieves an {@link EventModelSequenceTrainer} that fits the given parameters.
   *
   * @param trainParams The {@link TrainingParameters} to check for the trainer type.
   *                    Note: The entry {@link AbstractTrainer#ALGORITHM_PARAM} is used
   *                    to determine the type.
   * @param reportMap A {@link Map} that shall be used during initialization of
   *                  the {@link EventModelSequenceTrainer}.
   *
   * @return A valid {@link EventModelSequenceTrainer} for the configured {@code trainParams}.
   * @throws IllegalArgumentException Thrown if the trainer type could not be determined.
   */
  public static <T> EventModelSequenceTrainer<T> getEventModelSequenceTrainer(
          TrainingParameters trainParams, Map<String, String> reportMap) {
    String trainerType = trainParams.getStringParameter(AbstractTrainer.ALGORITHM_PARAM,null);

    if (trainerType != null) {
      final EventModelSequenceTrainer<T> trainer;
      if (BUILTIN_TRAINERS.containsKey(trainerType)) {
        trainer = TrainerFactory.createBuiltinTrainer(BUILTIN_TRAINERS.get(trainerType));
      } else {
        trainer = ExtensionLoader.instantiateExtension(EventModelSequenceTrainer.class, trainerType);
      }
      trainer.init(trainParams, reportMap);
      return trainer;
    }
    else {
      throw new IllegalArgumentException("Trainer type couldn't be determined!");
    }
  }

  /**
   * Retrieves an {@link EventTrainer} that fits the given parameters.
   *
   * @param trainParams The {@link TrainingParameters} to check for the trainer type.
   *                    Note: The entry {@link AbstractTrainer#ALGORITHM_PARAM} is used
   *                    to determine the type. If the type is not defined, the
   *                    {@link GISTrainer#MAXENT_VALUE} will be used.
   * @param reportMap A {@link Map} that shall be used during initialization of
   *                  the {@link EventTrainer}.
   *
   * @return A valid {@link EventTrainer} for the configured {@code trainParams}.
   */
  public static EventTrainer getEventTrainer(
          TrainingParameters trainParams, Map<String, String> reportMap) {

    // if the trainerType is not defined -- use the GISTrainer.
    String trainerType = trainParams.getStringParameter(
            AbstractTrainer.ALGORITHM_PARAM, GISTrainer.MAXENT_VALUE);

    final EventTrainer trainer;
    if (BUILTIN_TRAINERS.containsKey(trainerType)) {
      trainer = TrainerFactory.createBuiltinTrainer(BUILTIN_TRAINERS.get(trainerType));
    } else {
      trainer = ExtensionLoader.instantiateExtension(EventTrainer.class, trainerType);
    }
    trainer.init(trainParams, reportMap);
    return trainer;
  }

  /**
   * @param trainParams The {@link TrainingParameters} to validate. Must not be {@code null}.
   * @return {@code true} if the {@code trainParams} could be validated, {@code false} otherwise.
   */
  public static boolean isValid(TrainingParameters trainParams) {

    // TODO: Need to validate all parameters correctly ... error prone?!
    String algorithmName = trainParams.getStringParameter(AbstractTrainer.ALGORITHM_PARAM,null);

    // If a trainer type can be determined, then the trainer is valid!
    if (algorithmName != null &&
        !(BUILTIN_TRAINERS.containsKey(algorithmName) || getTrainerType(trainParams) != null)) {
      return false;
    }

    try {
      // require that the Cutoff and the number of iterations be an integer.
      // if they are not set, the default values will be ok.
      trainParams.getIntParameter(AbstractTrainer.CUTOFF_PARAM, 0);
      trainParams.getIntParameter(AbstractTrainer.ITERATIONS_PARAM, 0);
    }
    catch (NumberFormatException e) {
      return false;
    }

    // no reason to require that the dataIndexer be a 1-pass or 2-pass data indexer.
    trainParams.getStringParameter(AbstractEventTrainer.DATA_INDEXER_PARAM, null);

    // TODO: Check data indexing ...
    return true;
  }

  @SuppressWarnings("unchecked")
  private static <T extends Trainer> T createBuiltinTrainer(Class<? extends Trainer> trainerClass) {
    Trainer theTrainer = null;
    if (trainerClass != null) {
      try {
        Constructor<? extends Trainer> c = trainerClass.getConstructor();
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
