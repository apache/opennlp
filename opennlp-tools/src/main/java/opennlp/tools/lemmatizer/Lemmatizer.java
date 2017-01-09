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

package opennlp.tools.lemmatizer;

/**
 * The interface for lemmatizers.
 */
public interface Lemmatizer {

  /**
   * Generates lemma tags for the word and postag returning the result in an array.
   *
   * @param toks an array of the tokens
   * @param tags an array of the pos tags
   *
   * @return an array of lemma classes for each token in the sequence.
   */
  String[] lemmatize(String[] toks, String tags[]);

}
