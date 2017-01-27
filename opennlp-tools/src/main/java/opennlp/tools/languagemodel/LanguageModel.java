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

package opennlp.tools.languagemodel;

import opennlp.tools.util.StringList;

/**
 * A language model can calculate the probability <i>p</i> (between 0 and 1) of a
 * certain {@link opennlp.tools.util.StringList sequence of tokens}, given its underlying vocabulary.
 */
public interface LanguageModel {

  /**
   * Calculate the probability of a series of tokens (e.g. a sentence), given a vocabulary
   *
   * @param tokens the text tokens to calculate the probability for
   * @return the probability of the given text tokens in the vocabulary
   */
  double calculateProbability(StringList tokens);

  /**
   * Predict the most probable output sequence of tokens, given an input sequence of tokens
   *
   * @param tokens a sequence of tokens
   * @return the most probable subsequent token sequence
   */
  StringList predictNextTokens(StringList tokens);

}
