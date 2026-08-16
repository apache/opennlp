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

import opennlp.embeddings.index.TurboQuantIndex;
import opennlp.embeddings.index.VectorIndex;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pins the bounded in-memory vector search listing in {@code embeddings.xml}. */
class VectorIndexUsageExampleTest {

  @Test
  void testBuildFreezeAndQuery(@TempDir Path modelDirectory) throws IOException {
    EmbeddingTestFixtures.writeAnalogyDirectory(modelDirectory);

    final StaticEmbeddingModel model = StaticEmbeddingModel.load(modelDirectory);
    final VectorIndex index = new TurboQuantIndex(model.dimension(), 4, 42L);

    index.add("royal-article", model.embed("king queen"));
    index.add("fruit-article", model.embed("apple"));
    index.freeze();

    final List<VectorIndex.Hit> hits = index.topK(model.embed("king"), 5);
    assertEquals(2, hits.size());
    assertEquals("royal-article", hits.get(0).id());
  }
}
