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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureSelectionTest {

  private static List<Set<String>> docFeatureSets;
  private static List<Map<String, Integer>> docTfMaps;
  private static List<String> docLabels;

  @BeforeAll
  static void setUp() {
    // Doc 0: sports - "goal team match"
    // Doc 1: sports - "goal player score"
    // Doc 2: tech   - "code software algorithm"
    // Doc 3: tech   - "code data server"
    docFeatureSets = new ArrayList<>();
    docTfMaps = new ArrayList<>();
    docLabels = new ArrayList<>();

    addDoc(Map.of("goal", 2, "team", 1, "match", 1), "sports");
    addDoc(Map.of("goal", 1, "player", 1, "score", 1), "sports");
    addDoc(Map.of("code", 2, "software", 1, "algorithm", 1), "technology");
    addDoc(Map.of("code", 1, "data", 1, "server", 1), "technology");
  }

  private static void addDoc(Map<String, Integer> tf, String label) {
    docTfMaps.add(tf);
    docFeatureSets.add(tf.keySet());
    docLabels.add(label);
  }

  @Test
  void testInformationGainScores() {
    Map<String, Double> scores = FeatureSelection.computeInformationGain(
        docFeatureSets, docLabels);
    assertNotNull(scores);
    assertFalse(scores.isEmpty());

    // "goal" only in sports, "code" only in tech -> high IG
    // "team" only in one doc -> less discriminative overall
    assertTrue(scores.get("goal") > 0);
    assertTrue(scores.get("code") > 0);
  }

  @Test
  void testChiSquareScores() {
    Map<String, Double> scores = FeatureSelection.computeChiSquare(
        docFeatureSets, docLabels);
    assertNotNull(scores);
    assertFalse(scores.isEmpty());

    // Features exclusive to one class should have high chi-square
    assertTrue(scores.get("goal") > 0);
    assertTrue(scores.get("code") > 0);
  }

  @Test
  void testTermFrequencyScores() {
    Map<String, Double> scores = FeatureSelection.computeTermFrequency(docTfMaps);
    assertNotNull(scores);

    // "goal" appears 3 times total (2+1), "code" appears 3 times (2+1)
    assertEquals(3.0, scores.get("goal"));
    assertEquals(3.0, scores.get("code"));
    assertEquals(1.0, scores.get("team"));
  }

  @Test
  void testDocumentFrequencyScores() {
    Map<String, Double> scores = FeatureSelection.computeDocumentFrequency(docFeatureSets);
    assertNotNull(scores);

    // "goal" in 2 docs, "code" in 2 docs, "team" in 1 doc
    assertEquals(2.0, scores.get("goal"));
    assertEquals(2.0, scores.get("code"));
    assertEquals(1.0, scores.get("team"));
  }

  @Test
  void testSelectTopFeaturesByInformationGain() {
    Set<String> selected = FeatureSelection.selectTopFeatures(
        docFeatureSets, docTfMaps, docLabels,
        FeatureSelectionStrategy.INFORMATION_GAIN, 3);

    assertNotNull(selected);
    assertEquals(3, selected.size());
  }

  @Test
  void testSelectTopFeaturesByChiSquare() {
    Set<String> selected = FeatureSelection.selectTopFeatures(
        docFeatureSets, docTfMaps, docLabels,
        FeatureSelectionStrategy.CHI_SQUARE, 3);

    assertNotNull(selected);
    assertEquals(3, selected.size());
  }

  @Test
  void testSelectTopFeaturesByTermFrequency() {
    Set<String> selected = FeatureSelection.selectTopFeatures(
        docFeatureSets, docTfMaps, docLabels,
        FeatureSelectionStrategy.TERM_FREQUENCY, 2);

    assertNotNull(selected);
    assertEquals(2, selected.size());
    // "goal" and "code" both have tf=3, so they should be in the top 2
    assertTrue(selected.contains("goal"));
    assertTrue(selected.contains("code"));
  }

  @Test
  void testSelectTopFeaturesByDocumentFrequency() {
    Set<String> selected = FeatureSelection.selectTopFeatures(
        docFeatureSets, docTfMaps, docLabels,
        FeatureSelectionStrategy.DOCUMENT_FREQUENCY, 2);

    assertNotNull(selected);
    assertEquals(2, selected.size());
    assertTrue(selected.contains("goal"));
    assertTrue(selected.contains("code"));
  }

  @Test
  void testSelectAllWhenMaxIsNegative() {
    Set<String> selected = FeatureSelection.selectTopFeatures(
        docFeatureSets, docTfMaps, docLabels,
        FeatureSelectionStrategy.INFORMATION_GAIN, -1);

    // All 8 unique features
    assertEquals(10, selected.size());
  }

  @Test
  void testSelectAllWhenMaxExceedsTotalFeatures() {
    Set<String> selected = FeatureSelection.selectTopFeatures(
        docFeatureSets, docTfMaps, docLabels,
        FeatureSelectionStrategy.TERM_FREQUENCY, 100);

    assertEquals(10, selected.size());
  }
}
