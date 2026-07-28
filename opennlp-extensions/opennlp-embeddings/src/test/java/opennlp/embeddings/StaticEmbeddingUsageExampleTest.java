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
package opennlp.embeddings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.embeddings.StaticEmbeddingModel.Normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the cookbook path documented in {@code embeddings.xml}: load a
 * {@link StaticEmbeddingModel}, embed a sentence, and call {@code similarity},
 * {@code mostSimilar}, and {@code analogy}.
 */
public class StaticEmbeddingUsageExampleTest {

  @Test
  void testEmbedSimilarityNeighborsAndAnalogy(@TempDir Path dir) throws IOException {
    final StaticEmbeddingModel model =
        EmbeddingTestFixtures.loadAnalogyModel(dir, Normalization.NONE);

    final float[] vector = model.embed("king");
    assertEquals(2, vector.length);

    assertEquals(1.0, model.similarity("king", "king"), 1e-5);

    final List<Neighbor> neighbors = model.mostSimilar("king", 5);
    assertTrue(!neighbors.isEmpty());
    assertEquals("king", neighbors.get(0).token());

    final List<Neighbor> analogy = model.analogy("man", "king", "woman", 1);
    assertEquals(1, analogy.size());
    assertEquals("queen", analogy.get(0).token());
  }
}
