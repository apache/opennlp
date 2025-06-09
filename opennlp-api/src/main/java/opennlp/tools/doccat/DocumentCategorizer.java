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
   * Categorizes the given {@code text} provided as tokens along with
   * the provided {@code extraInformation}.
   *
   * @param text The tokens of text to categorize.
   * @param extraInformation The extra information used for this context.
   * @return The per category probabilities.
   */
  double[] categorize(String[] text, Map<String, Object> extraInformation);

  /**
   * Categorizes the given {@code text}, provided in separate tokens.
   * 
   * @param text The tokens of text to categorize.
   * @return The per category probabilities.
   */
  double[] categorize(String[] text);

  /**
   * Retrieves the best category from previously generated {@code outcome} probabilities
   *
   * @param outcome An array of computed outcome probabilities.
   * @return The best category represented as String.
   */
  String getBestCategory(double[] outcome);

  /**
   * Retrieves the index of a certain category.
   *
   * @param category The category for which the {@code index} is to be found.
   * @return The index.
   */
  int getIndex(String category);

  /**
   * Retrieves the category at a given {@code index}.
   *
   * @param index The index for which the {@code category} shall be found.
   * @return The category represented as String.
   */
  String getCategory(int index);

  /**
   * Retrieves the number of categories.
   *
   * @return The no. of categories.
   */
  int getNumberOfCategories();

  /**
   * Retrieves the name of the category associated with the given probabilities.
   *
   * @param results The probabilities of each category.
   * @return The name of the outcome.
   */
  String getAllResults(double[] results);

  /**
   * Retrieves a {@link Map} in which the key is the category name and the value is the score.
   *
   * @param text The tokenized input text to classify.
   * @return A {@link Map} with the score as a key.
   */
  Map<String, Double> scoreMap(String[] text);

  /**
   * Retrieves a {@link SortedMap} of the scores sorted in ascending order,
   * together with their associated categories.
   * <p> 
   * Many categories can have the same score, hence the {@link Set} as value.
   *
   * @param text the input text to classify
   * @return A {@link SortedMap} with the score as a key.
   */
  SortedMap<Double, Set<String>> sortedScoreMap(String[] text);

}

