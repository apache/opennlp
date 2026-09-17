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

package opennlp.tools.ml.model;

/**
 * Creates the {@link AbstractModelReader} of one model format.
 * <p>
 * Each machine learning module registers a factory for its reader in the
 * {@link opennlp.tools.util.ext.ExtensionRegistry} under the reader class name
 * named by {@link opennlp.tools.ml.AlgorithmType#getReaderClazz()}, so that
 * {@link GenericModelReader} can create it without reflection.
 */
@FunctionalInterface
public interface ModelReaderFactory {

  /**
   * @param dataReader The {@link DataReader} the model is read from. Must not be {@code null}.
   * @return A reader for the format.
   */
  AbstractModelReader create(DataReader dataReader);
}
