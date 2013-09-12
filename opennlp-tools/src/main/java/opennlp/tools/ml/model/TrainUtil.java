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

import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.SequenceTrainer;
import opennlp.tools.ml.TrainerFactory;

public class TrainUtil {
  
  // TODO: Need a way to report results and settings back for inclusion in model ...
  
  /**
   * @deprecated Use {@link TrainerFactory#getEventTrainer(Map, Map)} to get an
   *             {@link EventTrainer} instead.
   */
  public static AbstractModel train(EventStream events, Map<String, String> trainParams, Map<String, String> reportMap) 
      throws IOException {
    
    if(!TrainerFactory.isSupportEvent(trainParams)) {
      throw new IllegalArgumentException("EventTrain is not supported");
    }
    EventTrainer trainer = TrainerFactory.getEventTrainer(trainParams, reportMap);
    
    return trainer.train(events);
  }
  
  /**
   * @deprecated Use {@link TrainerFactory#getSequenceTrainer(Map, Map)} to get an
   *             {@link SequenceTrainer} instead.
   */
  public static AbstractModel train(SequenceStream events, Map<String, String> trainParams,
      Map<String, String> reportMap) throws IOException {
    
    if(!TrainerFactory.isSupportSequence(trainParams)) {
      throw new IllegalArgumentException("EventTrain is not supported");
    }
    SequenceTrainer trainer = TrainerFactory.getSequenceTrainer(trainParams, reportMap);
    
    return trainer.train(events);
  }
}
