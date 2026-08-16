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
package opennlp.embeddings.eval;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.embeddings.StaticEmbeddingModel;
import opennlp.embeddings.corpus.CasePassage;
import opennlp.embeddings.corpus.DictionaryEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HNSW baseline on the same tiny deterministic fixture the evaluator tests use: metric
 * ranges and structure, the self-labeling ground truth, and both report renderings.
 */
class HnswBaselineTest {

  private static final List<CasePassage> PASSAGES = List.of(
      new CasePassage("p-king", "King v. Queen", "1 U.S. 1", "1900-01-01", "1",
          "king queen king queen"),
      new CasePassage("p-man", "Man v. Woman", "1 U.S. 2", "1900-01-02", "1",
          "man woman man woman"),
      new CasePassage("p-apple", "In re Apple", "1 U.S. 3", "1900-01-03", "1",
          "apple apple apple apple"));

  private static final List<DictionaryEntry> DICTIONARY = List.of(
      new DictionaryEntry("KING", "royal man on the throne, a king"),
      new DictionaryEntry("APPLE", "an apple, a fruit"));

  @TempDir
  static Path modelDir;
  private static StaticEmbeddingModel model;

  @BeforeAll
  static void loadModel() throws IOException {
    opennlp.embeddings.EmbeddingTestFixtures.writeAnalogyDirectory(modelDir);
    model = StaticEmbeddingModel.load(modelDir);
  }

  /** {@return one baseline run over the deterministic fixture} */
  private HnswBaseline.Report run() {
    return HnswBaseline.run(model, PASSAGES, DICTIONARY, 2);
  }

  @Test
  void testMetricsAreCompleteAndInRange() {
    final HnswBaseline.Report report = run();

    assertEquals(PASSAGES.size(), report.passageCount());
    assertEquals(PASSAGES.size(), report.indexedPassageCount());
    assertEquals(DICTIONARY.size(), report.headwordCount());
    assertEquals(DICTIONARY.size(), report.indexedHeadwordCount());
    assertEquals(model.dimension(), report.dimension());
    assertEquals(PASSAGES.size(), report.exact().rows());
    assertEquals(PASSAGES.size(), report.hnsw().rows());
    assertTrue(report.hnsw().storageBytesPerVector()
        >= model.dimension() * (double) Float.BYTES,
        "storage bytes/vector: " + report.hnsw().storageBytesPerVector());
    assertTrue(report.hnsw().queriesPerSecond() > 0);
    assertTrue(report.fidelityRecallAtK() >= 0 && report.fidelityRecallAtK() <= 1);
    assertTrue(report.fidelityAgreement() >= 0 && report.fidelityAgreement() <= 1);
  }

  @Test
  void testTheGraphSearchAgreesWithTheExactScanOnATinyCorpus() {
    final HnswBaseline.Report report = run();

    // Three vectors, one graph: the approximate search cannot miss.
    assertEquals(1.0, report.fidelityRecallAtK());
    assertEquals(1.0, report.fidelityAgreement());
    assertEquals(1.0, report.halfPassage().recallAtK(),
        "each half passage must retrieve its own passage");
  }

  @Test
  void testTheMarkdownAndTsvRenderTheSameNumbers() {
    final HnswBaseline.Report report = run();
    final String markdown = report.toMarkdown();
    final String tsv = report.toTsv();

    assertTrue(markdown.contains("| hnsw |"), markdown);
    assertTrue(markdown.contains("| exact |"), markdown);
    assertTrue(markdown.contains("storage bytes/vector"), markdown);
    assertTrue(markdown.contains("rank-1 agreement"), markdown);
    assertTrue(tsv.contains("hnsw.storageBytesPerVector\t"), tsv);
    assertTrue(tsv.contains("fidelity.recallAtK\t"), tsv);
    assertTrue(tsv.contains("halfPassage.hnsw.mrr\t"), tsv);
  }

  @Test
  void testAnEmptyDictionarySkipsThatEvaluation() {
    final HnswBaseline.Report report = HnswBaseline.run(model, PASSAGES, List.of(), 2);

    assertEquals(0, report.headwordCount());
    assertEquals(0, report.indexedHeadwordCount());
    assertTrue(report.toMarkdown().contains("Skipped: no dictionary entries"),
        report.toMarkdown());
    assertFalse(report.toTsv().contains("definitionToHeadword."), report.toTsv());
  }

  @Test
  void testInvalidArgumentsAreRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> HnswBaseline.run(null, PASSAGES, DICTIONARY, 2));
    assertThrows(IllegalArgumentException.class,
        () -> HnswBaseline.run(model, null, DICTIONARY, 2));
    assertThrows(IllegalArgumentException.class,
        () -> HnswBaseline.run(model, List.of(), DICTIONARY, 2));
    assertThrows(IllegalArgumentException.class,
        () -> HnswBaseline.run(model, PASSAGES, null, 2));
    assertThrows(IllegalArgumentException.class,
        () -> HnswBaseline.run(model, PASSAGES, DICTIONARY, 0));
  }

  @Test
  void testCollectionElementsAndIdentifiersUseTheEvaluatorValidation() {
    final CasePassage duplicatePassage = new CasePassage(PASSAGES.get(0).id(), "Duplicate",
        "1 U.S. 4", "1900-01-04", "1", "duplicate passage");
    final DictionaryEntry duplicateHeadword = new DictionaryEntry(
        DICTIONARY.get(0).headword(), "duplicate definition");

    assertEquals("passages must not contain null at index 1",
        assertThrows(IllegalArgumentException.class,
            () -> HnswBaseline.run(model,
                Arrays.asList(PASSAGES.get(0), null), DICTIONARY, 2))
            .getMessage());
    assertEquals("passage id must be unique: " + PASSAGES.get(0).id(),
        assertThrows(IllegalArgumentException.class,
            () -> HnswBaseline.run(model,
                List.of(PASSAGES.get(0), duplicatePassage), DICTIONARY, 2)).getMessage());
    assertEquals("dictionary must not contain null at index 1",
        assertThrows(IllegalArgumentException.class,
            () -> HnswBaseline.run(model, PASSAGES,
                Arrays.asList(DICTIONARY.get(0), null), 2)).getMessage());
    assertEquals("dictionary headword must be unique: " + DICTIONARY.get(0).headword(),
        assertThrows(IllegalArgumentException.class,
            () -> HnswBaseline.run(model, PASSAGES,
                List.of(DICTIONARY.get(0), duplicateHeadword), 2)).getMessage());
  }
}
