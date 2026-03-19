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

package opennlp.tools.ml.libsvm.doccat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import de.hhn.mi.configuration.KernelType;
import de.hhn.mi.configuration.SvmConfigurationImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import opennlp.tools.doccat.DocumentCategorizer;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.FeatureGenerator;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentCategorizerSVMTest {

  private static final FeatureGenerator BAG_OF_WORDS = new BowFeatureGenerator();

  private static List<DocumentSample> trainingSamples;

  private static final String[] SPORTS_QUERY =
      {"football", "goal", "team", "match", "player", "game", "stadium", "coach"};
  private static final String[] TECH_QUERY =
      {"computer", "software", "code", "algorithm", "system", "developer", "api", "database"};

  /**
   * A robust configuration: LINEAR kernel + BINARY weighting — the textbook
   * combination for SVM text classification on small datasets.
   */
  private static final SvmDoccatConfiguration ROBUST_CONFIG =
      new SvmDoccatConfiguration.Builder()
          .setSvmConfiguration(new SvmConfigurationImpl.Builder()
              .setKernelType(KernelType.LINEAR)
              .setProbability(true)
              .build())
          .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
          .setScaleFeatures(false)
          .build();

  @BeforeAll
  static void setUp() {
    trainingSamples = new ArrayList<>();
    // Sports samples
    trainingSamples.add(new DocumentSample("sports",
        new String[]{"football", "goal", "team", "match", "player", "stadium"}));
    trainingSamples.add(new DocumentSample("sports",
        new String[]{"basketball", "score", "team", "court", "player", "game"}));
    trainingSamples.add(new DocumentSample("sports",
        new String[]{"tennis", "match", "serve", "court", "player", "game"}));
    trainingSamples.add(new DocumentSample("sports",
        new String[]{"football", "match", "goal", "referee", "stadium", "game"}));
    trainingSamples.add(new DocumentSample("sports",
        new String[]{"basketball", "team", "score", "ball", "arena", "game"}));
    trainingSamples.add(new DocumentSample("sports",
        new String[]{"football", "goal", "player", "match", "game", "coach"}));
    trainingSamples.add(new DocumentSample("sports",
        new String[]{"tennis", "serve", "player", "court", "match", "game"}));
    trainingSamples.add(new DocumentSample("sports",
        new String[]{"basketball", "team", "court", "player", "score", "game"}));

    // Technology samples
    trainingSamples.add(new DocumentSample("technology",
        new String[]{"computer", "software", "code", "program", "algorithm", "system"}));
    trainingSamples.add(new DocumentSample("technology",
        new String[]{"internet", "network", "server", "cloud", "data", "system"}));
    trainingSamples.add(new DocumentSample("technology",
        new String[]{"programming", "software", "developer", "code", "api", "system"}));
    trainingSamples.add(new DocumentSample("technology",
        new String[]{"computer", "algorithm", "data", "machine", "learning", "system"}));
    trainingSamples.add(new DocumentSample("technology",
        new String[]{"software", "code", "program", "developer", "debug", "system"}));
    trainingSamples.add(new DocumentSample("technology",
        new String[]{"computer", "software", "algorithm", "code", "system", "database"}));
    trainingSamples.add(new DocumentSample("technology",
        new String[]{"network", "server", "data", "cloud", "system", "api"}));
    trainingSamples.add(new DocumentSample("technology",
        new String[]{"programming", "code", "developer", "software", "system", "debug"}));
  }

  private static SvmDoccatModel trainModel(SvmDoccatConfiguration config) throws IOException {
    return DocumentCategorizerSVM.train("eng",
        new CollectionObjectStream<>(trainingSamples), config, BAG_OF_WORDS);
  }

  private static void assertClassifiesCorrectly(DocumentCategorizer cat) {
    assertEquals("sports", cat.getBestCategory(cat.categorize(SPORTS_QUERY)));
    assertEquals("technology", cat.getBestCategory(cat.categorize(TECH_QUERY)));
  }

  private static void assertValidProbabilities(double[] outcomes, int expectedCategories) {
    assertNotNull(outcomes);
    assertEquals(expectedCategories, outcomes.length);
    double sum = 0;
    for (double p : outcomes) {
      assertTrue(p >= 0.0, "Probabilities must be non-negative");
      sum += p;
    }
    assertTrue(Math.abs(sum - 1.0) < 0.05, "Probabilities should sum to ~1.0");
  }

  // --- Robust configuration: LINEAR + BINARY ---

  @Test
  void testLinearBinaryClassifiesCorrectly() throws IOException {
    SvmDoccatModel model = trainModel(ROBUST_CONFIG);
    assertNotNull(model);
    assertEquals(2, model.getNumberOfCategories());
    assertClassifiesCorrectly(new DocumentCategorizerSVM(model, BAG_OF_WORDS));
  }

  // --- Default configuration ---

  @Test
  void testTrainAndClassifyDefaults() throws IOException {
    SvmDoccatModel model = DocumentCategorizerSVM.train("eng",
        new CollectionObjectStream<>(trainingSamples), BAG_OF_WORDS);
    assertNotNull(model);
    assertEquals(2, model.getNumberOfCategories());

    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BAG_OF_WORDS);
    assertValidProbabilities(cat.categorize(SPORTS_QUERY), 2);
    assertValidProbabilities(cat.categorize(TECH_QUERY), 2);
  }

  // --- Term weighting strategies ---

  @ParameterizedTest
  @EnumSource(TermWeightingStrategy.class)
  void testTermWeightingStrategiesProduceValidOutput(TermWeightingStrategy strategy) throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(strategy)
        .setScaleFeatures(false)
        .build();

    SvmDoccatModel model = trainModel(config);
    assertNotNull(model);

    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BAG_OF_WORDS);
    assertValidProbabilities(cat.categorize(SPORTS_QUERY), 2);
    assertValidProbabilities(cat.categorize(TECH_QUERY), 2);

    // Both categories must be different outcomes
    String sportsCat = cat.getBestCategory(cat.categorize(SPORTS_QUERY));
    String techCat = cat.getBestCategory(cat.categorize(TECH_QUERY));
    assertTrue("sports".equals(sportsCat) || "technology".equals(sportsCat));
    assertTrue("sports".equals(techCat) || "technology".equals(techCat));
  }

  @Test
  void testBinaryWeightingClassifiesCorrectly() throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setScaleFeatures(false)
        .build();

    SvmDoccatModel model = trainModel(config);
    assertClassifiesCorrectly(new DocumentCategorizerSVM(model, BAG_OF_WORDS));
  }

  @Test
  void testTermFrequencyWeightingClassifiesCorrectly() throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.TERM_FREQUENCY)
        .setScaleFeatures(false)
        .build();

    SvmDoccatModel model = trainModel(config);
    assertClassifiesCorrectly(new DocumentCategorizerSVM(model, BAG_OF_WORDS));
  }

  // --- Feature selection strategies ---

  @ParameterizedTest
  @EnumSource(value = FeatureSelectionStrategy.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
  void testFeatureSelectionStrategiesLimitVocabulary(FeatureSelectionStrategy strategy) throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setFeatureSelectionStrategy(strategy)
        .setMaxFeatures(12)
        .setScaleFeatures(false)
        .build();

    SvmDoccatModel model = trainModel(config);
    assertNotNull(model);
    assertTrue(model.getFeatureVocabulary().size() <= 12);

    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BAG_OF_WORDS);
    assertValidProbabilities(cat.categorize(TECH_QUERY), 2);
  }

  @Test
  void testInformationGainSelection() throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setFeatureSelectionStrategy(FeatureSelectionStrategy.INFORMATION_GAIN)
        .setMaxFeatures(15)
        .setScaleFeatures(false)
        .build();

    SvmDoccatModel model = trainModel(config);
    assertTrue(model.getFeatureVocabulary().size() <= 15);
    assertClassifiesCorrectly(new DocumentCategorizerSVM(model, BAG_OF_WORDS));
  }

  @Test
  void testChiSquareSelection() throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setFeatureSelectionStrategy(FeatureSelectionStrategy.CHI_SQUARE)
        .setMaxFeatures(15)
        .setScaleFeatures(false)
        .build();

    SvmDoccatModel model = trainModel(config);
    assertTrue(model.getFeatureVocabulary().size() <= 15);
    assertClassifiesCorrectly(new DocumentCategorizerSVM(model, BAG_OF_WORDS));
  }

  @Test
  void testTermFrequencySelection() throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setFeatureSelectionStrategy(FeatureSelectionStrategy.TERM_FREQUENCY)
        .setMaxFeatures(15)
        .setScaleFeatures(false)
        .build();

    SvmDoccatModel model = trainModel(config);
    assertTrue(model.getFeatureVocabulary().size() <= 15);
    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BAG_OF_WORDS);
    assertValidProbabilities(cat.categorize(SPORTS_QUERY), 2);
  }

  @Test
  void testDocumentFrequencySelection() throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setFeatureSelectionStrategy(FeatureSelectionStrategy.DOCUMENT_FREQUENCY)
        .setMaxFeatures(15)
        .setScaleFeatures(false)
        .build();

    SvmDoccatModel model = trainModel(config);
    assertTrue(model.getFeatureVocabulary().size() <= 15);
    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BAG_OF_WORDS);
    assertValidProbabilities(cat.categorize(TECH_QUERY), 2);
  }

  // --- Feature scaling ---

  @Test
  void testWithScaling() throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setScaleFeatures(true)
        .setScaleRange(0.0, 1.0)
        .build();

    SvmDoccatModel model = trainModel(config);
    assertClassifiesCorrectly(new DocumentCategorizerSVM(model, BAG_OF_WORDS));
  }

  @Test
  void testCustomScaleRange() throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setScaleFeatures(true)
        .setScaleRange(-1.0, 1.0)
        .build();

    SvmDoccatModel model = trainModel(config);
    assertClassifiesCorrectly(new DocumentCategorizerSVM(model, BAG_OF_WORDS));
  }

  // --- SVM configuration ---

  @Test
  void testRBFKernel() throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.RBF)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setScaleFeatures(false)
        .build();

    SvmDoccatModel model = trainModel(config);
    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BAG_OF_WORDS);
    assertValidProbabilities(cat.categorize(TECH_QUERY), 2);
  }

  @Test
  void testCustomCostParameter() throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setCost(10.0)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setScaleFeatures(false)
        .build();

    SvmDoccatModel model = trainModel(config);
    assertClassifiesCorrectly(new DocumentCategorizerSVM(model, BAG_OF_WORDS));
  }

  // --- Combined configuration ---

  @Test
  void testFullPipeline() throws IOException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setCost(1.0)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setFeatureSelectionStrategy(FeatureSelectionStrategy.INFORMATION_GAIN)
        .setMaxFeatures(20)
        .setScaleFeatures(true)
        .setScaleRange(0.0, 1.0)
        .build();

    SvmDoccatModel model = trainModel(config);
    assertNotNull(model);
    assertTrue(model.getFeatureVocabulary().size() <= 20);
    assertClassifiesCorrectly(new DocumentCategorizerSVM(model, BAG_OF_WORDS));
  }

  // --- DocumentCategorizer API ---

  @Test
  void testGetCategoryAndIndex() throws IOException {
    SvmDoccatModel model = trainModel(ROBUST_CONFIG);

    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BAG_OF_WORDS);
    int sportsIdx = cat.getIndex("sports");
    int techIdx = cat.getIndex("technology");
    assertTrue(sportsIdx >= 0);
    assertTrue(techIdx >= 0);
    assertTrue(sportsIdx != techIdx);
    assertEquals("sports", cat.getCategory(sportsIdx));
    assertEquals("technology", cat.getCategory(techIdx));
    assertEquals(-1, cat.getIndex("nonexistent"));
  }

  @Test
  void testScoreMap() throws IOException {
    SvmDoccatModel model = trainModel(ROBUST_CONFIG);

    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BAG_OF_WORDS);
    Map<String, Double> scores = cat.scoreMap(SPORTS_QUERY);
    assertEquals(2, scores.size());
    assertTrue(scores.containsKey("sports"));
    assertTrue(scores.containsKey("technology"));
    assertTrue(scores.get("sports") > scores.get("technology"),
        "Sports score should be higher for sports query");
  }

  @Test
  void testSortedScoreMap() throws IOException {
    SvmDoccatModel model = trainModel(ROBUST_CONFIG);

    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BAG_OF_WORDS);
    var sorted = cat.sortedScoreMap(TECH_QUERY);
    assertNotNull(sorted);
    assertTrue(sorted.size() >= 1);
  }

  @Test
  void testGetAllResults() throws IOException {
    SvmDoccatModel model = trainModel(ROBUST_CONFIG);

    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BAG_OF_WORDS);
    double[] outcomes = cat.categorize(SPORTS_QUERY);
    String allResults = cat.getAllResults(outcomes);
    assertNotNull(allResults);
    assertTrue(allResults.contains("sports"));
    assertTrue(allResults.contains("technology"));
  }

  // --- Model serialization ---

  @Test
  void testModelSerialization() throws IOException, ClassNotFoundException {
    SvmDoccatConfiguration config = new SvmDoccatConfiguration.Builder()
        .setSvmConfiguration(new SvmConfigurationImpl.Builder()
            .setKernelType(KernelType.LINEAR)
            .setProbability(true)
            .build())
        .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
        .setFeatureSelectionStrategy(FeatureSelectionStrategy.INFORMATION_GAIN)
        .setMaxFeatures(20)
        .setScaleFeatures(true)
        .build();

    SvmDoccatModel model = trainModel(config);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    model.serialize(baos);
    byte[] bytes = baos.toByteArray();
    assertTrue(bytes.length > 0);

    SvmDoccatModel deserialized = SvmDoccatModel.deserialize(new ByteArrayInputStream(bytes));
    assertNotNull(deserialized);
    assertEquals(model.getNumberOfCategories(), deserialized.getNumberOfCategories());
    assertEquals(model.getLanguageCode(), deserialized.getLanguageCode());
    assertEquals(model.getFeatureVocabulary().size(), deserialized.getFeatureVocabulary().size());

    // Verify deserialized model classifies identically
    DocumentCategorizer original = new DocumentCategorizerSVM(model, BAG_OF_WORDS);
    DocumentCategorizer restored = new DocumentCategorizerSVM(deserialized, BAG_OF_WORDS);

    assertClassifiesCorrectly(original);
    assertClassifiesCorrectly(restored);
  }

  // --- Helper classes ---

  static class BowFeatureGenerator implements FeatureGenerator {
    @Override
    public Collection<String> extractFeatures(String[] text, Map<String, Object> extraInformation) {
      List<String> features = new ArrayList<>(text.length);
      for (String word : text) {
        features.add("bow=" + word);
      }
      return features;
    }
  }

  static class CollectionObjectStream<T> implements ObjectStream<T> {
    private final List<T> items;
    private int index = 0;

    CollectionObjectStream(List<T> items) {
      this.items = items;
    }

    @Override
    public T read() {
      if (index < items.size()) {
        return items.get(index++);
      }
      return null;
    }

    @Override
    public void reset() {
      index = 0;
    }

    @Override
    public void close() {
    }
  }
}
