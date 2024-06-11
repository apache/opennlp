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

public interface ClassPathModelFinder {

  String OPENNLP_MODEL_JAR_PREFIX = "opennlp-models-*.jar";

  /**
   * Finds OpenNLP models within the classpath.
   *
   * @param reloadCache {@code true}, if the internal cache should explicitly be reloaded
   * @return A Set of {@link ClassPathModelEntry ClassPathModelEntries}. It might be empty.
   */
  Set<ClassPathModelEntry> findModels(boolean reloadCache);
}
