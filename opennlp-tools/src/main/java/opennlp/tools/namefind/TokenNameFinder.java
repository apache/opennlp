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

package opennlp.tools.namefind;

import opennlp.tools.util.Span;

/**
 * The interface for name finders which provide name tags for a sequence of tokens.
 */
public interface TokenNameFinder {

  /** Generates name tags for the given sequence, typically a sentence,
   * returning token spans for any identified names.
   *
   * @param tokens an array of the tokens or words of the sequence, typically a sentence.
   * @return an array of spans for each of the names identified.
   */
  Span[] find(String tokens[]);

  /**
   * Forgets all adaptive data which was collected during previous
   * calls to one of the find methods.
   *
   * This method is typical called at the end of a document.
   */
  void clearAdaptiveData();

}
