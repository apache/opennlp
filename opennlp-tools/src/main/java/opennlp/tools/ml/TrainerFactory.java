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

import opennlp.tools.ml.maxent.GIS;
import opennlp.tools.ml.maxent.quasinewton.QNTrainer;
import opennlp.tools.ml.perceptron.PerceptronTrainer;
import opennlp.tools.ml.perceptron.SimplePerceptronSequenceTrainer;

public class TrainerFactory {

  // built-in trainers
  private static final Map<String, Class> BUILTIN_TRAINERS;

  static {
    Map<String, Class> _trainers = new HashMap<String, Class>();
    _trainers.put(GIS.MAXENT_VALUE, GIS.class);
    _trainers.put(QNTrainer.MAXENT_QN_VALUE, QNTrainer.class);
    _trainers.put(PerceptronTrainer.PERCEPTRON_VALUE, PerceptronTrainer.class);
    _trainers.put(SimplePerceptronSequenceTrainer.PERCEPTRON_SEQUENCE_VALUE,
        SimplePerceptronSequenceTrainer.class);

    BUILTIN_TRAINERS = Collections.unmodifiableMap(_trainers);
  }

  public static boolean isSupportEvent(Map<String, String> trainParams) {
    if (trainParams.get(AbstractTrainer.TRAINER_TYPE_PARAM) != null) {
      if(EventTrainer.EVENT_VALUE.equals(trainParams
            .get(AbstractTrainer.TRAINER_TYPE_PARAM))) {
        return true;
      }
      return false;
    } else {
      return true; // default to event train
    }
  }

  public static boolean isSupportSequence(Map<String, String> trainParams) {
    if (SequenceTrainer.SEQUENCE_VALUE.equals(trainParams
        .get(AbstractTrainer.TRAINER_TYPE_PARAM))) {
      return true;
    }
    return false;
  }

  public static SequenceTrainer getSequenceTrainer(
      Map<String, String> trainParams, Map<String, String> reportMap) {
    String trainerType = getTrainerType(trainParams);
    if (BUILTIN_TRAINERS.containsKey(trainerType)) {
      return TrainerFactory.<SequenceTrainer> create(
          BUILTIN_TRAINERS.get(trainerType), trainParams, reportMap);
    } else {
      return TrainerFactory.<SequenceTrainer> create(trainerType, trainParams,
          reportMap);
    }
  }

  public static EventTrainer getEventTrainer(Map<String, String> trainParams,
      Map<String, String> reportMap) {
    String trainerType = getTrainerType(trainParams);
    if(trainerType == null) {
      // default to MAXENT
      return new GIS(trainParams, reportMap);
    }
    
    if (BUILTIN_TRAINERS.containsKey(trainerType)) {
      return TrainerFactory.<EventTrainer> create(
          BUILTIN_TRAINERS.get(trainerType), trainParams, reportMap);
    } else {
      return TrainerFactory.<EventTrainer> create(trainerType, trainParams,
          reportMap);
    }
  }

  private static String getTrainerType(Map<String, String> trainParams) {
    return trainParams.get(AbstractTrainer.ALGORITHM_PARAM);
  }

  private static <T> T create(String className,
      Map<String, String> trainParams, Map<String, String> reportMap) {
    T theFactory = null;

    try {
      // TODO: won't work in OSGi!
      Class<T> trainerClass = (Class<T>) Class.forName(className);
      theFactory = create(trainerClass, trainParams, reportMap);
    } catch (Exception e) {
      String msg = "Could not instantiate the " + className
          + ". The initialization throw an exception.";
      System.err.println(msg);
      e.printStackTrace();
      throw new IllegalArgumentException(msg, e);
    }
    return theFactory;
  }

  private static <T> T create(Class<T> trainerClass,
      Map<String, String> trainParams, Map<String, String> reportMap) {
    T theTrainer = null;
    if (trainerClass != null) {
      try {
        Constructor<T> contructor = trainerClass.getConstructor(Map.class,
            Map.class);
        theTrainer = contructor.newInstance(trainParams, reportMap);
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
