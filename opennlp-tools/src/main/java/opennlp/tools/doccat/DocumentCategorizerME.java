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
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * A Max-Ent based implementation of {@link DocumentCategorizer}.
 */
public class DocumentCategorizerME implements DocumentCategorizer {
  
  private final DoccatModel model;
  private final DocumentCategorizerContextGenerator mContextGenerator;

  /**
   * Initializes a {@link DocumentCategorizerME} instance with a doccat model.
   * Default feature generation is used.
   *
   * @param model the {@link DoccatModel} to be used for categorization.
   */
  public DocumentCategorizerME(DoccatModel model) {
    this.model = model;
    this.mContextGenerator = new DocumentCategorizerContextGenerator(this.model
        .getFactory().getFeatureGenerators());
  }

  /**
   * Categorize the given {@code text} provided as tokens along with
   * the provided extra information.
   *
   * @param text The text tokens to categorize.
   * @param extraInformation Additional information for context to be used by the feature generator.
   * @return The per category probabilities.
   */
  @Override
  public double[] categorize(String[] text, Map<String, Object> extraInformation) {
    return model.getMaxentModel().eval(
        mContextGenerator.getContext(text, extraInformation));
  }
  
  @Override
  public double[] categorize(String[] text) {
    return this.categorize(text, Collections.emptyMap());
  }

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

  @Override
  public String getBestCategory(double[] outcome) {
    return model.getMaxentModel().getBestOutcome(outcome);
  }

  @Override
  public int getIndex(String category) {
    return model.getMaxentModel().getIndex(category);
  }

  @Override
  public String getCategory(int index) {
    return model.getMaxentModel().getOutcome(index);
  }

  @Override
  public int getNumberOfCategories() {
    return model.getMaxentModel().getNumOutcomes();
  }

  @Override
  public String getAllResults(double[] results) {
    return model.getMaxentModel().getAllOutcomes(results);
  }

  /**
   * Starts a training of a {@link DoccatModel} with the given parameters.
   *
   * @param lang The ISO conform language code.
   * @param samples The {@link ObjectStream} of {@link DocumentSample} used as input for training.
   * @param mlParams The {@link TrainingParameters} for the context of the training.
   * @param factory The {@link DoccatFactory} for creating related objects defined via {@code mlParams}.
   *
   * @return A valid, trained {@link DoccatModel} instance.
   * @throws IOException Thrown if IO errors occurred.
   */
  public static DoccatModel train(String lang, ObjectStream<DocumentSample> samples,
      TrainingParameters mlParams, DoccatFactory factory) throws IOException {

    Map<String, String> manifestInfoEntries = new HashMap<>();

    EventTrainer trainer = TrainerFactory.getEventTrainer(
        mlParams, manifestInfoEntries);

    MaxentModel model = trainer.train(
        new DocumentCategorizerEventStream(samples, factory.getFeatureGenerators()));

    return new DoccatModel(lang, model, manifestInfoEntries, factory);
  }
}
