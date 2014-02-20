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

import java.io.IOException;

import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.model.SequenceStream;

public abstract class AbstractEventModelSequenceTrainer extends AbstractTrainer implements
    EventModelSequenceTrainer {

  public AbstractEventModelSequenceTrainer() {
  }

  public abstract MaxentModel doTrain(SequenceStream events)
      throws IOException;

  public final MaxentModel train(SequenceStream events) throws IOException {

    if (!isValid()) {
      throw new IllegalArgumentException("trainParams are not valid!");
    }

    MaxentModel model = doTrain(events);
    addToReport(AbstractTrainer.TRAINER_TYPE_PARAM,
        EventModelSequenceTrainer.SEQUENCE_VALUE);
    return model;
  }

}
