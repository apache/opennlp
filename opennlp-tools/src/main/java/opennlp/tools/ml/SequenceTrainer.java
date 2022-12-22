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

import opennlp.tools.commons.Trainer;
import opennlp.tools.ml.model.SequenceClassificationModel;
import opennlp.tools.ml.model.SequenceStream;

public interface SequenceTrainer extends Trainer {

  String SEQUENCE_VALUE = "Sequence";

  /**
   * Trains a {@link SequenceClassificationModel} for given {@link SequenceStream<T> events}.
   *
   * @param events The input {@link SequenceStream<T> events}.
   * @param <T> The generic type of elements to process via the {@link SequenceStream}.
   *
   * @return The trained {@link SequenceClassificationModel}.
   * @throws IOException Thrown if IO errors occurred.
   */
  <T> SequenceClassificationModel<String> train(SequenceStream<T> events) throws IOException;
}
