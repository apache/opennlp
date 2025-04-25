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
package opennlp.tools.models;

import java.util.Set;

/**
 * Describes a scanner which detects OpenNLP specific model files in an applications's classpath.
 * If compatible models are present at runtime, each discovered element is made available
 * as {@link ClassPathModelEntry}.
 */
public interface ClassPathModelFinder {

  String OPENNLP_MODEL_JAR_PREFIX = "opennlp-models-*.jar";

  /**
   * Finds OpenNLP models available within an applications's classpath.
   *
   * @param reloadCache {@code true}, if the internal cache should explicitly be reloaded,
   *                    {@code false} otherwise.
   * @return A {@link Set} of {@link ClassPathModelEntry model entries}.
   *         It might be empty, if none were found.
   */
  Set<ClassPathModelEntry> findModels(boolean reloadCache);
}
