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

package opennlp.tools.doccat;

import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

/**
 * Interface for classes which categorize documents.
 */
public interface DocumentCategorizer {

  /**
   * Categorizes the given text, provided in separate tokens.
   *
   * @param text the tokens of text to categorize
   * @return per category probabilities
   */
  double[] categorize(String text[]);

  /**
   * Categorizes the given text, provided in separate tokens.
   *
   * @param text             the tokens of text to categorize
   * @param extraInformation optional extra information to pass for evaluation
   * @return per category probabilities
   * @deprecated will be removed after 1.7.1 release. Don't use it.
   */
  @Deprecated
  double[] categorize(String text[], Map<String, Object> extraInformation);

  /**
   * get the best category from previously generated outcome probabilities
   *
   * @param outcome a vector of outcome probabilities
   * @return the best category String
   */
  String getBestCategory(double[] outcome);

  /**
   * get the index of a certain category
   *
   * @param category the category
   * @return an index
   */
  int getIndex(String category);

  /**
   * get the category at a given index
   *
   * @param index the index
   * @return a category
   */
  String getCategory(int index);

  /**
   * get the number of categories
   *
   * @return the no. of categories
   */
  int getNumberOfCategories();

  /**
   * categorize a piece of text
   *
   * @param documentText the text to categorize
   * @return the probabilities of each category (sum up to 1)
   * @deprecated will be removed after 1.7.1 release. Don't use it.
   */
  @Deprecated
  double[] categorize(String documentText);

  /**
   * categorize a piece of text, providing extra metadata.
   *
   * @param documentText     the text to categorize
   * @param extraInformation extra metadata
   * @return the probabilities of each category (sum up to 1)
   */
  double[] categorize(String documentText, Map<String, Object> extraInformation);

  /**
   * get the name of the category associated with the given probabilties
   *
   * @param results the probabilities of each category
   * @return the name of the outcome
   */
  String getAllResults(double results[]);

  /**
   * Returns a map in which the key is the category name and the value is the score
   *
   * @param text the input text to classify
   * @return a map with the score as a key. The value is a Set of categories with the score.
   * @deprecated will be removed after 1.7.1 release. Don't use it.
   */
  @Deprecated
  Map<String, Double> scoreMap(String text);

  /**
   * Returns a map in which the key is the category name and the value is the score
   *
   * @param text the input text to classify
   * @return a map with the score as a key. The value is a Set of categories with the score.
   */
  Map<String, Double> scoreMap(String[] text);

  /**
   * Get a map of the scores sorted in ascending aorder together with their associated categories.
   * Many categories can have the same score, hence the Set as value
   *
   * @param text the input text to classify
   * @return a map with the score as a key. The value is a Set of categories with the score.
   * @deprecated will be removed after 1.7.1 release. Don't use it.
   */
  @Deprecated
  SortedMap<Double, Set<String>> sortedScoreMap(String text);

  /**
   * Get a map of the scores sorted in ascending aorder together with their associated categories.
   * Many categories can have the same score, hence the Set as value
   *
   * @param text the input text to classify
   * @return a map with the score as a key. The value is a Set of categories with the score.
   */
  SortedMap<Double, Set<String>> sortedScoreMap(String[] text);

}

