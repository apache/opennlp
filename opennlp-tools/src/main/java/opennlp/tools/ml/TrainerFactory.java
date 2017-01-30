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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import opennlp.tools.ml.maxent.GISTrainer;
import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
import opennlp.tools.ml.naivebayes.NaiveBayesTrainer;
import opennlp.tools.ml.perceptron.PerceptronTrainer;
import opennlp.tools.ml.perceptron.SimplePerceptronSequenceTrainer;
import opennlp.tools.util.ext.ExtensionLoader;
import opennlp.tools.util.ext.ExtensionNotLoadedException;

public class TrainerFactory {

  public enum TrainerType {
    EVENT_MODEL_TRAINER,
    EVENT_MODEL_SEQUENCE_TRAINER,
    SEQUENCE_TRAINER
  }

  // built-in trainers
  private static final Map<String, Class> BUILTIN_TRAINERS;

  static {
    Map<String, Class> _trainers = new HashMap<>();
    _trainers.put(GISTrainer.MAXENT_VALUE, GISTrainer.class);
    _trainers.put(QNTrainer.MAXENT_QN_VALUE, QNTrainer.class);
    _trainers.put(PerceptronTrainer.PERCEPTRON_VALUE, PerceptronTrainer.class);
    _trainers.put(SimplePerceptronSequenceTrainer.PERCEPTRON_SEQUENCE_VALUE,
        SimplePerceptronSequenceTrainer.class);
    _trainers.put(NaiveBayesTrainer.NAIVE_BAYES_VALUE, NaiveBayesTrainer.class);

    BUILTIN_TRAINERS = Collections.unmodifiableMap(_trainers);
  }

  /**
   * Determines the trainer type based on the ALGORITHM_PARAM value.
   *
   * @param trainParams - Map of training parameters
   * @return the trainer type or null if type couldn't be determined.
   */
  public static TrainerType getTrainerType(Map<String, String> trainParams) {

    String alogrithmValue = trainParams.get(AbstractTrainer.ALGORITHM_PARAM);

    // Check if it is defaulting to the MAXENT trainer
    if (alogrithmValue == null) {
      return TrainerType.EVENT_MODEL_TRAINER;
    }

    Class<?> trainerClass = BUILTIN_TRAINERS.get(alogrithmValue);

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
      ExtensionLoader.instantiateExtension(EventTrainer.class, alogrithmValue);
      return TrainerType.EVENT_MODEL_TRAINER;
    }
    catch (ExtensionNotLoadedException ignored) {
      // this is ignored
    }

    try {
      ExtensionLoader.instantiateExtension(EventModelSequenceTrainer.class, alogrithmValue);
      return TrainerType.EVENT_MODEL_SEQUENCE_TRAINER;
    }
    catch (ExtensionNotLoadedException ignored) {
      // this is ignored
    }

    try {
      ExtensionLoader.instantiateExtension(SequenceTrainer.class, alogrithmValue);
      return TrainerType.SEQUENCE_TRAINER;
    }
    catch (ExtensionNotLoadedException ignored) {
      // this is ignored
    }

    return null;
  }

  public static SequenceTrainer getSequenceModelTrainer(Map<String, String> trainParams,
      Map<String, String> reportMap) {
    String trainerType = trainParams.get(AbstractTrainer.ALGORITHM_PARAM);

    if (trainerType != null) {
      if (BUILTIN_TRAINERS.containsKey(trainerType)) {
        SequenceTrainer trainer =  TrainerFactory.<SequenceTrainer>createBuiltinTrainer(
            BUILTIN_TRAINERS.get(trainerType));
        trainer.init(trainParams, reportMap);
        return trainer;
      } else {
        SequenceTrainer trainer =
            ExtensionLoader.instantiateExtension(SequenceTrainer.class, trainerType);
        trainer.init(trainParams, reportMap);
        return trainer;
      }
    }
    else {
      throw new IllegalArgumentException("Trainer type couldn't be determined!");
    }
  }

  public static EventModelSequenceTrainer getEventModelSequenceTrainer(Map<String, String> trainParams,
      Map<String, String> reportMap) {
    String trainerType = trainParams.get(AbstractTrainer.ALGORITHM_PARAM);
    if (trainerType != null) {
      if (BUILTIN_TRAINERS.containsKey(trainerType)) {
        EventModelSequenceTrainer trainer = TrainerFactory.<EventModelSequenceTrainer>createBuiltinTrainer(
            BUILTIN_TRAINERS.get(trainerType));
        trainer.init(trainParams, reportMap);
        return trainer;
      } else {
        EventModelSequenceTrainer trainer =
            ExtensionLoader.instantiateExtension(EventModelSequenceTrainer.class, trainerType);
        trainer.init(trainParams, reportMap);
        return trainer;
      }
    }
    else {
      throw new IllegalArgumentException("Trainer type couldn't be determined!");
    }
  }

  public static EventTrainer getEventTrainer(Map<String, String> trainParams,
      Map<String, String> reportMap) {
    String trainerType = trainParams.get(AbstractTrainer.ALGORITHM_PARAM);
    if (trainerType == null) {
      // default to MAXENT
      AbstractEventTrainer trainer = new GISTrainer();
      trainer.init(trainParams, reportMap);
      return trainer;
    }
    else {
      if (BUILTIN_TRAINERS.containsKey(trainerType)) {
        EventTrainer trainer = TrainerFactory.<EventTrainer>createBuiltinTrainer(
            BUILTIN_TRAINERS.get(trainerType));
        trainer.init(trainParams, reportMap);
        return trainer;
      } else {
        EventTrainer trainer = ExtensionLoader.instantiateExtension(EventTrainer.class, trainerType);
        trainer.init(trainParams, reportMap);
        return trainer;
      }
    }
  }

  public static boolean isValid(Map<String, String> trainParams) {

    // TODO: Need to validate all parameters correctly ... error prone?!

    String algorithmName = trainParams.get(AbstractTrainer.ALGORITHM_PARAM);

    // If a trainer type can be determined, then the trainer is valid!
    if (algorithmName != null &&
        !(BUILTIN_TRAINERS.containsKey(algorithmName) || getTrainerType(trainParams) != null)) {
      return false;
    }

    try {
      String cutoffString = trainParams.get(AbstractTrainer.CUTOFF_PARAM);
      if (cutoffString != null) {
        Integer.parseInt(cutoffString);
      }

      String iterationsString = trainParams.get(AbstractTrainer.ITERATIONS_PARAM);
      if (iterationsString != null) {
        Integer.parseInt(iterationsString);
      }
    }
    catch (NumberFormatException e) {
      return false;
    }

    String dataIndexer = trainParams.get(AbstractEventTrainer.DATA_INDEXER_PARAM);

    if (dataIndexer != null) {
      if (!(AbstractEventTrainer.DATA_INDEXER_ONE_PASS_VALUE.equals(dataIndexer)
          || AbstractEventTrainer.DATA_INDEXER_TWO_PASS_VALUE.equals(dataIndexer))) {
        return false;
      }
    }

    // TODO: Check data indexing ...
    return true;
  }

  private static <T> T createBuiltinTrainer(Class<T> trainerClass) {
    T theTrainer = null;
    if (trainerClass != null) {
      try {
        Constructor<T> contructor = trainerClass.getConstructor();
        theTrainer = contructor.newInstance();
      } catch (Exception e) {
        String msg = "Could not instantiate the "
            + trainerClass.getCanonicalName()
            + ". The initialization throw an exception.";
        System.err.println(msg);
        e.printStackTrace();
        throw new IllegalArgumentException(msg, e);
      }
    }

    return theTrainer;
  }
}
