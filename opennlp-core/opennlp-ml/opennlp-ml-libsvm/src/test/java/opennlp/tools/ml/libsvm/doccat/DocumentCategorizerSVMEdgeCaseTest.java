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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.hhn.mi.configuration.KernelType;
import de.hhn.mi.configuration.SvmConfigurationImpl;
import org.junit.jupiter.api.Test;

import opennlp.tools.doccat.DocumentCategorizer;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.FeatureGenerator;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentCategorizerSVMEdgeCaseTest {

  private static final SvmDoccatConfiguration LINEAR_BINARY =
      new SvmDoccatConfiguration.Builder()
          .setSvmConfiguration(new SvmConfigurationImpl.Builder()
              .setKernelType(KernelType.LINEAR)
              .setProbability(true)
              .build())
          .setTermWeightingStrategy(TermWeightingStrategy.BINARY)
          .setScaleFeatures(false)
          .build();

  private static final FeatureGenerator BOW = (text, extra) -> {
    List<String> f = new ArrayList<>(text.length);
    for (String w : text) f.add("bow=" + w);
    return f;
  };

  // --- Null/empty argument validation ---

  @Test
  void testTrainNullSamplesThrows() {
    assertThrows(NullPointerException.class, () ->
        DocumentCategorizerSVM.train("eng", null, BOW));
  }

  @Test
  void testTrainNullFeatureGeneratorsThrows() {
    assertThrows(NullPointerException.class, () ->
        DocumentCategorizerSVM.train("eng", emptyStream(), (FeatureGenerator[]) null));
  }

  @Test
  void testTrainEmptyFeatureGeneratorsThrows() {
    assertThrows(IllegalArgumentException.class, () ->
        DocumentCategorizerSVM.train("eng", emptyStream(), new FeatureGenerator[0]));
  }

  @Test
  void testTrainNullConfigThrows() {
    assertThrows(NullPointerException.class, () ->
        DocumentCategorizerSVM.train("eng", emptyStream(), (SvmDoccatConfiguration) null, BOW));
  }

  @Test
  void testConstructorNullModelThrows() {
    assertThrows(NullPointerException.class, () ->
        new DocumentCategorizerSVM(null, BOW));
  }

  @Test
  void testConstructorNullFeatureGeneratorsThrows() {
    assertThrows(NullPointerException.class, () -> {
      SvmDoccatModel model = trainMinimalModel();
      new DocumentCategorizerSVM(model, (FeatureGenerator[]) null);
    });
  }

  @Test
  void testConstructorEmptyFeatureGeneratorsThrows() {
    assertThrows(IllegalArgumentException.class, () -> {
      SvmDoccatModel model = trainMinimalModel();
      new DocumentCategorizerSVM(model, new FeatureGenerator[0]);
    });
  }

  // --- Classification with unknown features ---

  @Test
  void testClassifyWithUnknownTokens() throws IOException {
    SvmDoccatModel model = trainMinimalModel();
    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BOW);

    // Tokens not seen during training
    double[] outcomes = cat.categorize(new String[]{"unknown1", "unknown2", "unknown3"});
    assertNotNull(outcomes);
    assertEquals(2, outcomes.length);
    // Should still produce valid probabilities
    String best = cat.getBestCategory(outcomes);
    assertNotNull(best);
    assertTrue("a".equals(best) || "b".equals(best));
  }

  @Test
  void testClassifyWithMixOfKnownAndUnknown() throws IOException {
    SvmDoccatModel model = trainMinimalModel();
    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BOW);

    // Mix of known ("x") and unknown ("zzz")
    double[] outcomes = cat.categorize(new String[]{"x", "zzz"});
    assertNotNull(outcomes);
    assertEquals(2, outcomes.length);
  }

  @Test
  void testClassifyWithExtraInformation() throws IOException {
    SvmDoccatModel model = trainMinimalModel();
    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BOW);

    // Extra information is ignored by BagOfWords but shouldn't break anything
    double[] outcomes = cat.categorize(new String[]{"x", "y"},
        Map.of("key", "value"));
    assertNotNull(outcomes);
    assertEquals(2, outcomes.length);
  }

  // --- Multiple feature generators ---

  @Test
  void testMultipleFeatureGenerators() throws IOException {
    FeatureGenerator bow = (text, extra) -> {
      List<String> f = new ArrayList<>();
      for (String w : text) f.add("bow=" + w);
      return f;
    };
    FeatureGenerator bigram = (text, extra) -> {
      List<String> f = new ArrayList<>();
      for (int i = 0; i < text.length - 1; i++) {
        f.add("bg=" + text[i] + "_" + text[i + 1]);
      }
      return f;
    };

    List<DocumentSample> samples = List.of(
        new DocumentSample("a", new String[]{"x", "y", "z"}),
        new DocumentSample("a", new String[]{"x", "y", "w"}),
        new DocumentSample("b", new String[]{"p", "q", "r"}),
        new DocumentSample("b", new String[]{"p", "q", "s"}));

    SvmDoccatModel model = DocumentCategorizerSVM.train("eng",
        new CollectionObjectStream<>(samples), LINEAR_BINARY, bow, bigram);

    // Vocabulary should contain both bow= and bg= prefixed features
    boolean hasBow = model.getFeatureVocabulary().keySet().stream()
        .anyMatch(k -> k.startsWith("bow="));
    boolean hasBigram = model.getFeatureVocabulary().keySet().stream()
        .anyMatch(k -> k.startsWith("bg="));
    assertTrue(hasBow, "Should have bag-of-words features");
    assertTrue(hasBigram, "Should have bigram features");

    DocumentCategorizer cat = new DocumentCategorizerSVM(model, bow, bigram);
    assertNotNull(cat.categorize(new String[]{"x", "y"}));
  }

  // --- Three or more categories ---

  @Test
  void testThreeCategories() throws IOException {
    List<DocumentSample> samples = new ArrayList<>();
    samples.add(new DocumentSample("red", new String[]{"apple", "tomato", "cherry"}));
    samples.add(new DocumentSample("red", new String[]{"apple", "strawberry", "cherry"}));
    samples.add(new DocumentSample("green", new String[]{"lime", "leaf", "grass"}));
    samples.add(new DocumentSample("green", new String[]{"lime", "mint", "grass"}));
    samples.add(new DocumentSample("blue", new String[]{"sky", "ocean", "sapphire"}));
    samples.add(new DocumentSample("blue", new String[]{"sky", "sea", "sapphire"}));

    SvmDoccatModel model = DocumentCategorizerSVM.train("eng",
        new CollectionObjectStream<>(samples), LINEAR_BINARY, BOW);

    assertEquals(3, model.getNumberOfCategories());
    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BOW);
    double[] outcomes = cat.categorize(new String[]{"sky", "ocean", "sapphire"});
    assertEquals(3, outcomes.length);

    // All probabilities should be non-negative and sum to ~1
    double sum = 0;
    for (double p : outcomes) {
      assertTrue(p >= 0.0);
      sum += p;
    }
    assertTrue(Math.abs(sum - 1.0) < 0.05);
  }

  // --- GetIndex for non-existent category ---

  @Test
  void testGetIndexNonExistent() throws IOException {
    SvmDoccatModel model = trainMinimalModel();
    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BOW);
    assertEquals(-1, cat.getIndex("nonexistent_category"));
  }

  // --- GetAllResults format ---

  @Test
  void testGetAllResultsFormat() throws IOException {
    SvmDoccatModel model = trainMinimalModel();
    DocumentCategorizer cat = new DocumentCategorizerSVM(model, BOW);
    double[] outcomes = cat.categorize(new String[]{"x", "y"});

    String all = cat.getAllResults(outcomes);
    // Should contain category names and bracketed scores
    assertTrue(all.contains("["));
    assertTrue(all.contains("]"));
    for (int i = 0; i < cat.getNumberOfCategories(); i++) {
      assertTrue(all.contains(cat.getCategory(i)));
    }
  }

  // --- Helpers ---

  private SvmDoccatModel trainMinimalModel() throws IOException {
    List<DocumentSample> samples = List.of(
        new DocumentSample("a", new String[]{"x", "y"}),
        new DocumentSample("a", new String[]{"x", "z"}),
        new DocumentSample("b", new String[]{"p", "q"}),
        new DocumentSample("b", new String[]{"p", "r"}));

    return DocumentCategorizerSVM.train("eng",
        new CollectionObjectStream<>(samples), LINEAR_BINARY, BOW);
  }

  private ObjectStream<DocumentSample> emptyStream() {
    return new CollectionObjectStream<>(List.of());
  }

  static class CollectionObjectStream<T> implements ObjectStream<T> {
    private final List<T> items;
    private int index = 0;
    CollectionObjectStream(List<T> items) { this.items = items; }
    @Override public T read() { return index < items.size() ? items.get(index++) : null; }
    @Override public void reset() { index = 0; }
    @Override public void close() { }
  }
}
