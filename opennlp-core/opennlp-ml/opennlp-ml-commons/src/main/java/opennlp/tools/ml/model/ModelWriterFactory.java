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

import java.io.DataOutputStream;

/**
 * Creates the {@link AbstractModelWriter} of one model format.
 * <p>
 * Each machine learning module registers a factory for its writer in the
 * {@link opennlp.tools.util.ext.ExtensionRegistry} under the writer class name
 * named by {@link opennlp.tools.ml.AlgorithmType#getWriterClazz()}, so that
 * {@link GenericModelWriter} can create it without reflection.
 */
@FunctionalInterface
public interface ModelWriterFactory {

  /**
   * @param model The {@link AbstractModel} to write. Must not be {@code null}.
   * @param dos The {@link DataOutputStream} to write to. Must not be {@code null}.
   * @return A writer for the format.
   */
  AbstractModelWriter create(AbstractModel model, DataOutputStream dos);
}
