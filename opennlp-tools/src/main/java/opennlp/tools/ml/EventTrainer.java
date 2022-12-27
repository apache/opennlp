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
import opennlp.tools.ml.model.DataIndexer;
import opennlp.tools.ml.model.Event;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.util.ObjectStream;

/**
 * A specialized {@link Trainer} that is based on an {@link Event} approach.
 */
public interface EventTrainer extends Trainer {

  String EVENT_VALUE = "Event";

  /**
   * Trains a {@link MaxentModel} for given {@link ObjectStream<Event> events}.
   *
   * @param events The input {@link ObjectStream<Event> events}.
   *
   * @return The trained {@link MaxentModel}.
   * @throws IOException Thrown if IO errors occurred.
   */
  MaxentModel train(ObjectStream<Event> events) throws IOException;

  /**
   * Trains a {@link MaxentModel} for given {@link ObjectStream<Event> events}.
   *
   * @param indexer The input {@link DataIndexer indexer} to use.
   *
   * @return The trained {@link MaxentModel}.
   * @throws IOException Thrown if IO errors occurred.
   */
  MaxentModel train(DataIndexer indexer) throws IOException;
}
