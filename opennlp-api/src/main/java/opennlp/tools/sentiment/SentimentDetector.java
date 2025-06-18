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

package opennlp.tools.sentiment;

import opennlp.tools.util.Span;

public interface SentimentDetector {

  /**
   * Conducts a sentiment prediction for the specifed sentence.
   *
   * @param sentence The text to be analysed for its sentiment.
   * @return The predicted sentiment.
   */
  String predict(String sentence);

  /**
   * Conducts a sentiment prediction for the specifed sentence.
   *
   * @param tokens The text to be analysed for its sentiment.
   * @return The predicted sentiment.
   */
  String predict(String[] tokens);

  /**
   * Generates sentiment tags for the given sequence, typically a sentence,
   * returning token spans for any identified sentiments.
   *
   * @param tokens
   *          an array of the tokens or words of the sequence, typically a
   *          sentence
   * @return an array of spans for each of the names identified.
   */
  Span[] find(String[] tokens);

  /**
   * Generates sentiment tags for the given sequence, typically a sentence,
   * returning token spans for any identified sentiments.
   *
   * @param tokens
   *          an array of the tokens or words of the sequence, typically a
   *          sentence.
   * @param additionalContext
   *          features which are based on context outside of the sentence but
   *          which should also be used.
   *
   * @return an array of spans for each of the names identified.
   */
  Span[] find(String[] tokens, String[][] additionalContext);
}
