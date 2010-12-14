/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreemnets.  See the NOTICE file distributed with
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

package opennlp.tools.coref.mention;

/** 
 * Interface to provide dictionary information to the coreference module assuming a
 * hierarchically structured dictionary (such as WordNet) is available.
 */
public interface Dictionary {

  /**
   * Returns the lemmas of the specified word with the specified part-of-speech.
   * 
   * @param word The word whose lemmas are desired.
   * @param pos The part-of-speech of the specified word.
   * @return The lemmas of the specified word given the specified part-of-speech.
   */
  public String[] getLemmas(String word, String pos);

  /**
   * Returns a key indicating the specified sense number of the specified
   * lemma with the specified part-of-speech.
   * 
   * @param lemma The lemmas for which the key is desired.
   * @param pos The pos for which the key is desired.
   * @param senseNumber The sense number for which the key is desired.
   * @return a key indicating the specified sense number of the specified
   * lemma with the specified part-of-speech.
   */
 public  String getSenseKey(String lemma, String pos, int senseNumber);

  /**
   * Returns the number of senses in the dictionary for the specified lemma.
   * 
   * @param lemma A lemmatized form of the word to look up.
   * @param pos The part-of-speech for the lemma.
   * @return the number of senses in the dictionary for the specified lemma.
   */
  public int getNumSenses(String lemma, String pos);

  /**
   * Returns an array of keys for each parent of the specified sense number of the specified lemma with the specified part-of-speech.
   * 
   * @param lemma A lemmatized form of the word to look up.
   * @param pos The part-of-speech for the lemma.
   * @param senseNumber The sense number for which the parent keys are desired.
   * @return an array of keys for each parent of the specified sense number of the specified lemma with the specified part-of-speech.
   */
  public String[] getParentSenseKeys(String lemma, String pos, int senseNumber);
}
