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

import opennlp.dl.doccat.DocumentCategorizerDL;
import opennlp.dl.doccat.scoring.AverageClassificationScoringStrategy;
import opennlp.dl.namefinder.NameFinderDL;
import opennlp.dl.vectors.SentenceVectorsDL;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests {@link AbstractDL#close()} on the deep-learning components.
 */
class CloseTest {

  @TempDir
  Path dir;

  private File model;
  private File vocab;

  @BeforeEach
  void setUp() throws Exception {
    model = TestModels.model(dir);
    vocab = TestModels.vocab(dir);
  }

  @Test
  void testCloseTwiceIsNoOp() throws Exception {
    final AbstractDL[] components = {
        new SentenceVectorsDL(model, vocab),
        new NameFinderDL(model, vocab, TestModels.LABELS, TestModels.SINGLE_SENTENCE),
        new DocumentCategorizerDL(model, vocab, TestModels.LABELS,
            new AverageClassificationScoringStrategy(), new InferenceOptions())
    };
    for (final AbstractDL component : components) {
      component.close();
      assertDoesNotThrow(component::close);
    }
  }

  @Test
  void testCloseKeepsSharedEnvironmentUsable() throws Exception {
    final float[] expected;
    try (SentenceVectorsDL first = new SentenceVectorsDL(model, vocab)) {
      expected = first.getVectors("hello world");
    }
    try (SentenceVectorsDL second = new SentenceVectorsDL(model, vocab)) {
      assertArrayEquals(expected, second.getVectors("hello world"));
    }
  }
}
