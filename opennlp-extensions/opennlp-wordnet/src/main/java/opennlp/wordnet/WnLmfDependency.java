/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.wordnet;

import opennlp.tools.commons.ThreadSafe;

/**
 * One WN-LMF {@code Requires} declaration. It identifies another lexicon and the required
 * version, but does not resolve or load that lexicon.
 *
 * @param ref     The required lexicon id. Must not be {@code null} or empty.
 * @param version The required lexicon version. Must not be {@code null} or empty.
 */
@ThreadSafe
public record WnLmfDependency(String ref, String version) {

  /**
   * Creates a dependency descriptor.
   *
   * @throws IllegalArgumentException Thrown if a component is {@code null} or empty.
   */
  public WnLmfDependency {
    if (ref == null || ref.isEmpty()) {
      throw new IllegalArgumentException("Ref must not be null or empty");
    }
    if (version == null || version.isEmpty()) {
      throw new IllegalArgumentException("Version must not be null or empty");
    }
  }
}
