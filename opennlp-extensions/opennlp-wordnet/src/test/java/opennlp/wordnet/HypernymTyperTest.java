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

package opennlp.wordnet;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Tests {@link HypernymTyper} against the taxonomy from {@link SynsetSimilarityTest}. */
public class HypernymTyperTest {

  /**
   * @return A typer over the shared fixture taxonomy with person and location anchors.
   */
  private static HypernymTyper typer() {
    return new HypernymTyper(taxonomy(),
        Map.of("person", "person", "location", "location"));
  }

  /**
   * @return The taxonomy shared with {@link SynsetSimilarityTest}.
   */
  private static SynsetSimilarityTest.FixtureKnowledgeBase taxonomy() {
    return SynsetSimilarityTest.taxonomy();
  }

  @Test
  void testTypesThroughHypernymAndInstanceChains() {
    final HypernymTyper typer = typer();
    Assertions.assertEquals(Optional.of("person"), typer.type("chemist"));
    Assertions.assertEquals(Optional.of("location"), typer.type("paris"));
    Assertions.assertEquals(Optional.of("person"), typer.type("person"));
    Assertions.assertEquals(Optional.of("location"), typer.typeSynset("n8"));
  }

  @Test
  void testUnreachableAnchorsYieldEmpty() {
    final HypernymTyper typer = typer();
    Assertions.assertEquals(Optional.empty(), typer.type("abstract"));
    Assertions.assertEquals(Optional.empty(), typer.type("unknownword"));
    Assertions.assertEquals(Optional.empty(), typer.typeSynset("n12"));
    Assertions.assertEquals(Optional.empty(), typer.typeSynset("missing"));
  }

  @Test
  void testNearestAnchorWins() {
    final Map<String, String> anchors = new LinkedHashMap<>();
    anchors.put("person", "person");
    anchors.put("scientist", "scientist");
    final HypernymTyper typer = new HypernymTyper(taxonomy(), anchors);
    Assertions.assertEquals(Optional.of("scientist"), typer.type("chemist"));
    Assertions.assertEquals(Optional.of("scientist"), typer.type("scientist"));
    // An ancestor of an anchor cannot reach that anchor through an upward relation.
    Assertions.assertEquals(Optional.empty(), typer.type("organism"));
  }

  @Test
  void testConflictingLabelsForTheSameSynsetAreRejected() {
    final Map<String, String> anchors = new LinkedHashMap<>();
    anchors.put("dog", "animal");
    anchors.put("domestic dog", "pet");

    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(WnLmfReaderTest.fixture(), anchors));
  }

  @Test
  void testInvalidArguments() {
    final Map<String, String> anchors = Map.of("person", "person");
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(null, anchors));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(taxonomy(), null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(taxonomy(), Map.of()));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(taxonomy(), Map.of(" ", "person")));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(taxonomy(), Map.of("person", "\u00A0")));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new HypernymTyper(taxonomy(), Map.of("notaword", "label")));
    final HypernymTyper typer = typer();
    Assertions.assertThrows(IllegalArgumentException.class, () -> typer.type(null));
    Assertions.assertThrows(IllegalArgumentException.class, () -> typer.type(" "));
    Assertions.assertThrows(IllegalArgumentException.class, () -> typer.typeSynset(null));
  }
}
