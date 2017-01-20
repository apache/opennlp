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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Maxent implementation of {@link DocumentCategorizer}.
 */
public class DocumentCategorizerME implements DocumentCategorizer {

  /**
   * Shared default thread safe feature generator.
   */
  private static FeatureGenerator defaultFeatureGenerator = new BagOfWordsFeatureGenerator();

  private DoccatModel model;
  private DocumentCategorizerContextGenerator mContextGenerator;

  /**
   * Initializes the current instance with a doccat model and custom feature
   * generation. The feature generation must be identical to the configuration
   * at training time.
   *
   * @param model             the doccat model
   * @param featureGenerators the feature generators
   * @deprecated train a {@link DoccatModel} with a specific
   * {@link DoccatFactory} to customize the {@link FeatureGenerator}s
   */
  @Deprecated
  public DocumentCategorizerME(DoccatModel model, FeatureGenerator... featureGenerators) {
    this.model = model;
    this.mContextGenerator = new DocumentCategorizerContextGenerator(featureGenerators);
  }

  /**
   * Initializes the current instance with a doccat model. Default feature
   * generation is used.
   *
   * @param model the doccat model
   */
  public DocumentCategorizerME(DoccatModel model) {
    this.model = model;
    this.mContextGenerator = new DocumentCategorizerContextGenerator(this.model
        .getFactory().getFeatureGenerators());
  }

  @Override
  public double[] categorize(String[] text, Map<String, Object> extraInformation) {
    return model.getMaxentModel().eval(
        mContextGenerator.getContext(text, extraInformation));
  }

  /**
   * Categorizes the given text.
   * @param text the text to categorize
   */
  public double[] categorize(String text[]) {
    return this.categorize(text, Collections.emptyMap());
  }

  /**
   * Categorizes the given text. The Tokenizer is obtained from
   * {@link DoccatFactory#getTokenizer()} and defaults to
   * {@link SimpleTokenizer}.
   * @deprecated will be removed after 1.7.1 release. Don't use it.
   */
  @Deprecated
  @Override
  public double[] categorize(String documentText,
      Map<String, Object> extraInformation) {
    Tokenizer tokenizer = model.getFactory().getTokenizer();
    return categorize(tokenizer.tokenize(documentText), extraInformation);
  }

  /**
   * Categorizes the given text. The text is tokenized with the SimpleTokenizer
   * before it is passed to the feature generation.
   * @deprecated will be removed after 1.7.1 release. Don't use it.
   */
  @Deprecated
  public double[] categorize(String documentText) {
    Tokenizer tokenizer = model.getFactory().getTokenizer();
    return categorize(tokenizer.tokenize(documentText), Collections.emptyMap());
  }

  /**
   * Returns a map in which the key is the category name and the value is the score
   *
   * @param text the input text to classify
   * @return the score map
   * @deprecated will be removed after 1.7.1 release. Don't use it.
   */
  @Deprecated
  public Map<String, Double> scoreMap(String text) {
    Map<String, Double> probDist = new HashMap<>();

    double[] categorize = categorize(text);
    int catSize = getNumberOfCategories();
    for (int i = 0; i < catSize; i++) {
      String category = getCategory(i);
      probDist.put(category, categorize[getIndex(category)]);
    }
    return probDist;
  }

  /**
   * Returns a map in which the key is the category name and the value is the score
   *
   * @param text the input text to classify
   * @return the score map
   */
  @Override
  public Map<String, Double> scoreMap(String[] text) {
    Map<String, Double> probDist = new HashMap<>();

    double[] categorize = categorize(text);
    int catSize = getNumberOfCategories();
    for (int i = 0; i < catSize; i++) {
      String category = getCategory(i);
      probDist.put(category, categorize[getIndex(category)]);
    }
    return probDist;
  }

  /**
   * Returns a map with the score as a key in ascending order.
   * The value is a Set of categories with the score.
   * Many categories can have the same score, hence the Set as value
   *
   * @param text the input text to classify
   * @return the sorted score map
   * @deprecated will be removed after 1.7.1 release. Don't use it.
   */
  @Deprecated
  @Override
  public SortedMap<Double, Set<String>> sortedScoreMap(String text) {
    SortedMap<Double, Set<String>> descendingMap = new TreeMap<>();
    double[] categorize = categorize(text);
    int catSize = getNumberOfCategories();
    for (int i = 0; i < catSize; i++) {
      String category = getCategory(i);
      double score = categorize[getIndex(category)];
      if (descendingMap.containsKey(score)) {
        descendingMap.get(score).add(category);
      } else {
        Set<String> newset = new HashSet<>();
        newset.add(category);
        descendingMap.put(score, newset);
      }
    }
    return descendingMap;
  }

  /**
   * Returns a map with the score as a key in ascending order.
   * The value is a Set of categories with the score.
   * Many categories can have the same score, hence the Set as value
   *
   * @param text the input text to classify
   * @return the sorted score map
   */
  @Override
  public SortedMap<Double, Set<String>> sortedScoreMap(String[] text) {
    SortedMap<Double, Set<String>> descendingMap = new TreeMap<>();
    double[] categorize = categorize(text);
    int catSize = getNumberOfCategories();
    for (int i = 0; i < catSize; i++) {
      String category = getCategory(i);
      double score = categorize[getIndex(category)];
      if (descendingMap.containsKey(score)) {
        descendingMap.get(score).add(category);
      } else {
        Set<String> newset = new HashSet<>();
        newset.add(category);
        descendingMap.put(score, newset);
      }
    }
    return descendingMap;
  }

  public String getBestCategory(double[] outcome) {
    return model.getMaxentModel().getBestOutcome(outcome);
  }

  public int getIndex(String category) {
    return model.getMaxentModel().getIndex(category);
  }

  public String getCategory(int index) {
    return model.getMaxentModel().getOutcome(index);
  }

  public int getNumberOfCategories() {
    return model.getMaxentModel().getNumOutcomes();
  }

  public String getAllResults(double results[]) {
    return model.getMaxentModel().getAllOutcomes(results);
  }

  public static DoccatModel train(String languageCode, ObjectStream<DocumentSample> samples,
      TrainingParameters mlParams, DoccatFactory factory)
          throws IOException {

    Map<String, String> manifestInfoEntries = new HashMap<>();

    EventTrainer trainer = TrainerFactory.getEventTrainer(
        mlParams.getSettings(), manifestInfoEntries);

    MaxentModel model = trainer.train(
        new DocumentCategorizerEventStream(samples, factory.getFeatureGenerators()));

    return new DoccatModel(languageCode, model, manifestInfoEntries, factory);
  }
}
