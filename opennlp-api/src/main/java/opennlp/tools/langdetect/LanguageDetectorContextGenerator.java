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

package opennlp.tools.langdetect;

import java.io.Serializable;

/**
 * A context generator interface for {@link LanguageDetector}.
 */
public interface LanguageDetectorContextGenerator extends Serializable {

  /**
   * Retrieves the contexts for a {@code document} using character ngrams.
   *
   * @param document The textual input used to extract context from.
   *
   * @return An array of contexts on which a model basis its decisions.
   */
  <T extends CharSequence> T[] getContext(CharSequence document);
}
