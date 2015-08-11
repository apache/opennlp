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
import java.io.ObjectStreamException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import opennlp.tools.ml.AbstractTrainer;
import opennlp.tools.ml.EventTrainer;
import opennlp.tools.ml.TrainerFactory;
import opennlp.tools.ml.model.MaxentModel;
import opennlp.tools.ml.naivebayes.NaiveBayesModel;
import opennlp.tools.ml.naivebayes.NaiveBayesTrainer;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import opennlp.tools.util.model.ModelUtil;

/**
 * Naive Bayes implementation of {@link DocumentCategorizer}.
 */
public class DocumentCategorizerNB implements DocumentCategorizer {

  /**
   * Shared default thread safe feature generator.
   */
  private static FeatureGenerator defaultFeatureGenerator = new BagOfWordsFeatureGenerator();

  private DoccatModel model;
  private DocumentCategorizerContextGenerator mContextGenerator;

  /**
   * Initializes a the current instance with a doccat model and custom feature
   * generation. The feature generation must be identical to the configuration
   * at training time.
   *
   * @param model
   * @param featureGenerators
   * @deprecated train a {@link DoccatModel} with a specific
   * {@link DoccatFactory} to customize the {@link FeatureGenerator}s
   */
  public DocumentCategorizerNB(DoccatModel model, FeatureGenerator... featureGenerators) {
    this.model = model;
    this.mContextGenerator = new DocumentCategorizerContextGenerator(featureGenerators);
  }

  /**
   * Initializes the current instance with a doccat model. Default feature
   * generation is used.
   *
   * @param model
   */
  public DocumentCategorizerNB(DoccatModel model) {
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
   *
   * @param text
   */
  public double[] categorize(String text[]) {
    return this.categorize(text, Collections.<String, Object>emptyMap());
  }

  /**
   * Categorizes the given text. The Tokenizer is obtained from
   * {@link DoccatFactory#getTokenizer()} and defaults to
   * {@link SimpleTokenizer}.
   */
  @Override
  public double[] categorize(String documentText,
                             Map<String, Object> extraInformation) {
    Tokenizer tokenizer = model.getFactory().getTokenizer();
    return categorize(tokenizer.tokenize(documentText), extraInformation);
  }

  /**
   * Categorizes the given text. The text is tokenized with the SimpleTokenizer
   * before it is passed to the feature generation.
   */
  public double[] categorize(String documentText) {
    Tokenizer tokenizer = model.getFactory().getTokenizer();
    return categorize(tokenizer.tokenize(documentText),
        Collections.<String, Object>emptyMap());
  }

  /**
   * Returns a map in which the key is the category name and the value is the score
   *
   * @param text the input text to classify
   * @return
   */
  public Map<String, Double> scoreMap(String text) {
    Map<String, Double> probDist = new HashMap<String, Double>();

    double[] categorize = categorize(text);
    int catSize = getNumberOfCategories();
    for (int i = 0; i < catSize; i++) {
      String category = getCategory(i);
      probDist.put(category, categorize[getIndex(category)]);
    }
    return probDist;

  }

  /**
   * Returns a map with the score as a key in ascendng order. The value is a Set of categories with the score.
   * Many categories can have the same score, hence the Set as value
   *
   * @param text the input text to classify
   * @return
   */
  public SortedMap<Double, Set<String>> sortedScoreMap(String text) {
    SortedMap<Double, Set<String>> descendingMap = new TreeMap<Double, Set<String>>();
    double[] categorize = categorize(text);
    int catSize = getNumberOfCategories();
    for (int i = 0; i < catSize; i++) {
      String category = getCategory(i);
      double score = categorize[getIndex(category)];
      if (descendingMap.containsKey(score)) {
        descendingMap.get(score).add(category);
      } else {
        Set<String> newset = new HashSet<String>();
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

  /**
   * @deprecated Use
   * {@link #train(String, ObjectStream, TrainingParameters, DoccatFactory)}
   * instead.
   */
  public static DoccatModel train(String languageCode, ObjectStream<DocumentSample> samples,
                                  TrainingParameters mlParams, FeatureGenerator... featureGenerators)
      throws IOException {

    if (featureGenerators.length == 0) {
      featureGenerators = new FeatureGenerator[]{defaultFeatureGenerator};
    }

    Map<String, String> manifestInfoEntries = new HashMap<String, String>();

    mlParams.put(AbstractTrainer.ALGORITHM_PARAM, NaiveBayesTrainer.NAIVE_BAYES_VALUE);

    NaiveBayesModel nbModel = getTrainedInnerModel(samples, mlParams, manifestInfoEntries, featureGenerators);

    return new DoccatModel(languageCode, nbModel, manifestInfoEntries);
  }

  public static DoccatModel train(String languageCode, ObjectStream<DocumentSample> samples,
                                  TrainingParameters mlParams, DoccatFactory factory)
      throws IOException {

    Map<String, String> manifestInfoEntries = new HashMap<String, String>();

    mlParams.put(AbstractTrainer.ALGORITHM_PARAM, NaiveBayesTrainer.NAIVE_BAYES_VALUE);

    NaiveBayesModel nbModel = getTrainedInnerModel(samples, mlParams, manifestInfoEntries, factory.getFeatureGenerators());

    return new DoccatModel(languageCode, nbModel, manifestInfoEntries, factory);
  }

  protected static NaiveBayesModel getTrainedInnerModel(
      ObjectStream<DocumentSample> samples, TrainingParameters mlParams,
      Map<String, String> manifestInfoEntries,
      FeatureGenerator... featureGenerators) throws IOException {
    if (!TrainerFactory.isSupportEvent(mlParams.getSettings())) {
      throw new IllegalArgumentException("EventTrain is not supported");
    }
    EventTrainer trainer = TrainerFactory.getEventTrainer(mlParams.getSettings(), manifestInfoEntries);
    MaxentModel model = trainer.train(new DocumentCategorizerEventStream(samples, featureGenerators));

    NaiveBayesModel nbModel = null;
    if (model instanceof NaiveBayesModel) {
      nbModel = (NaiveBayesModel) model;
    }
    return nbModel;
  }

  /**
   * Trains a doccat model with default feature generation.
   *
   * @param languageCode
   * @param samples
   * @return the trained doccat model
   * @throws IOException
   * @throws ObjectStreamException
   * @deprecated Use
   * {@link #train(String, ObjectStream, TrainingParameters, DoccatFactory)}
   * instead.
   */
  public static DoccatModel train(String languageCode, ObjectStream<DocumentSample> samples) throws IOException {
    return train(languageCode, samples, ModelUtil.createDefaultTrainingParameters(), defaultFeatureGenerator);
  }
}
