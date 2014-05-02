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

public interface DictionaryLemmatizer {

  /**
   * Returns the lemma of the specified word with the specified part-of-speech.
   *
   * @param word The word whose lemmas are desired.
   * @param postag The part-of-speech of the specified word.
   * @return The lemma of the specified word given the specified part-of-speech.
   */
  public String lemmatize(String word, String postag);


}
