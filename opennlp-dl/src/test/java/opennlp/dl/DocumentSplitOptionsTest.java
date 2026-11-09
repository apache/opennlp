/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package opennlp.dl;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import opennlp.dl.doccat.DocumentCategorizerDL;
import opennlp.dl.doccat.scoring.AverageClassificationScoringStrategy;
import opennlp.dl.namefinder.NameFinderDL;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the validation of {@link InferenceOptions#getDocumentSplitSize()} and
 * {@link InferenceOptions#getSplitOverlapSize()} by {@link DocumentCategorizerDL} and
 * {@link NameFinderDL}.
 */
class DocumentSplitOptionsTest {

  private static final String TEXT = "hello world hello world hello";

  @TempDir
  Path dir;

  private File model;
  private File vocab;

  @BeforeEach
  void setUp() throws Exception {
    model = TestModels.model(dir);
    vocab = TestModels.vocab(dir);
  }

  private static InferenceOptions options(final int splitSize, final int overlapSize) {
    final InferenceOptions options = new InferenceOptions();
    options.setDocumentSplitSize(splitSize);
    options.setSplitOverlapSize(overlapSize);
    return options;
  }

  private DocumentCategorizerDL categorizer(final InferenceOptions options) throws Exception {
    return new DocumentCategorizerDL(model, vocab, TestModels.LABELS,
        new AverageClassificationScoringStrategy(), options);
  }

  private NameFinderDL nameFinder(final InferenceOptions options) throws Exception {
    return new NameFinderDL(model, vocab, TestModels.LABELS, options, TestModels.SINGLE_SENTENCE);
  }

  @ParameterizedTest
  @CsvSource({"0, 0", "-1, 0", "10, -1", "10, 10", "10, 11", "1, 1"})
  void testInvalidSplitOptionsAreRejected(final int splitSize, final int overlapSize) {
    final InferenceOptions options = options(splitSize, overlapSize);

    final IllegalArgumentException doccat =
        assertThrows(IllegalArgumentException.class, () -> categorizer(options));
    final IllegalArgumentException namefinder =
        assertThrows(IllegalArgumentException.class, () -> nameFinder(options));

    assertTrue(doccat.getMessage().contains("SplitSize")
        || doccat.getMessage().contains("splitOverlapSize"), doccat.getMessage());
    assertEquals(doccat.getMessage(), namefinder.getMessage());
  }

  @ParameterizedTest
  @CsvSource({"1, 0", "2, 1", "3, 0", "10, 9", "250, 50"})
  void testValidSplitOptionsAreAccepted(final int splitSize, final int overlapSize)
      throws Exception {
    try (NameFinderDL nameFinder = nameFinder(options(splitSize, overlapSize));
         DocumentCategorizerDL categorizer = categorizer(options(splitSize, overlapSize))) {
      assertArrayEquals(new Span[0], nameFinder.find(TEXT.split(" ")));
      assertEquals(TestModels.LABELS.size(), categorizer.getNumberOfCategories());
    }
  }

  @Test
  void testSplitOptionsChangedAfterConstructionAreRejected() throws Exception {
    final InferenceOptions options = options(2, 1);
    try (NameFinderDL nameFinder = nameFinder(options);
         DocumentCategorizerDL categorizer = categorizer(options)) {
      options.setSplitOverlapSize(2);

      assertThrows(IllegalArgumentException.class, () -> nameFinder.find(TEXT.split(" ")));
      // DocumentCategorizerDL reports failures as an empty result.
      assertArrayEquals(new double[0], categorizer.categorize(new String[] {TEXT}));
    }
  }
}
