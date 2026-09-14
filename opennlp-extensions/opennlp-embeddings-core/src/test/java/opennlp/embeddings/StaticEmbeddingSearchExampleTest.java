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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Demonstrates semantic search with the query and documents shown in {@code embeddings.xml}.
 */
public class StaticEmbeddingSearchExampleTest {

  /** A scored document, as the manual's listing declares it. */
  record Scored(String document, double score) {
  }

  @Test
  void testRanksDocumentsByCosineSimilarityToTheQuery(@TempDir Path dir) throws IOException {
    EmbeddingTestFixtures.writeSearchDirectory(dir);
    final StaticEmbeddingModel model = StaticEmbeddingModel.load(dir);

    final String query = "home espresso machine";
    final List<String> documents = List.of(
        "How do I brew espresso at home?",
        "The history of tea in East Asia",
        "Best grinders for pour-over coffee");

    final List<Scored> results = new ArrayList<>();
    for (final String document : documents) {
      results.add(new Scored(document, model.similarity(query, document)));
    }
    results.sort(Comparator.comparingDouble(Scored::score).reversed());

    assertEquals(List.of(
            "How do I brew espresso at home?",
            "Best grinders for pour-over coffee",
            "The history of tea in East Asia"),
        results.stream().map(Scored::document).toList());
    assertTrue(results.get(0).score() > results.get(1).score());
    assertTrue(results.get(1).score() > results.get(2).score());
  }
}
