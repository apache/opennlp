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

package opennlp.tools.commons;

import java.util.Map;

import opennlp.tools.util.TrainingParameters;

/**
 * Represents a common base for training implementations.
 */
public interface Trainer {

  /**
   * Conducts the initialization of an {@link Trainer} via
   * {@link TrainingParameters} and a {@link Map report map}.
   *
   * @param trainParams The {@link TrainingParameters} to use.
   * @param reportMap The {@link Map} instance used as report map.
   */
  void init(TrainingParameters trainParams, Map<String, String> reportMap);

}
