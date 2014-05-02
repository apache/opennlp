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

package opennlp.tools.ml.model;

import java.io.IOException;
import java.util.Map;

import opennlp.tools.ml.EventModelSequenceTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.util.ObjectStream;

public class TrainUtil {

  /**
   * @deprecated Use {@link TrainerFactory#isValid(Map)} instead.
   */
  public static boolean isValid(Map<String, String> trainParams) {
    return TrainerFactory.isValid(trainParams);
  }

  // TODO: Need a way to report results and settings back for inclusion in model ...

  /**
   * @deprecated Use {@link TrainerFactory#getEventTrainer(Map, Map)} to get an
   *             {@link EventTrainer} instead.
   */
  public static MaxentModel train(ObjectStream<Event> events, Map<String, String> trainParams, Map<String, String> reportMap)
      throws IOException {

    if(!TrainerFactory.isSupportEvent(trainParams)) {
      throw new IllegalArgumentException("EventTrain is not supported");
    }
    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, reportMap);

    return trainer.train(events);
  }

  /**
   * Detects if the training algorithm requires sequence based feature
   * generation or not.
   *
   * @deprecated Use {@link TrainerFactory#isSequenceTraining(Map)} instead.
   */
  public static boolean isSequenceTraining(Map<String, String> trainParams) {
	return TrainerFactory.isSupportSequence(trainParams);
  }

  /**
   * @deprecated Use {@link TrainerFactory#getSequenceTrainer(Map, Map)} to get an
   *             {@link EventModelSequenceTrainer} instead.
   */
  public static MaxentModel train(SequenceStream events, Map<String, String> trainParams,
      Map<String, String> reportMap) throws IOException {

    if(!TrainerFactory.isSupportSequence(trainParams)) {
      throw new IllegalArgumentException("EventTrain is not supported");
    }
    EventModelSequenceTrainer trainer = TrainerFactory.getEventModelSequenceTrainer(trainParams, reportMap);

    return trainer.train(events);
  }
}
