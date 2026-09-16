/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.wordnet.WordNetPOS;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Smoke tests against complete, pinned OMW 2.0 releases fetched by the developer script. */
class WnLmfOmwIntegrationTest {

  @ParameterizedTest(name = "OMW 2.0 {0}")
  @MethodSource("wordnets")
  void testCompleteOmwRelease(String language, String relativeFile, String lemma,
                              String expectedSynset) throws IOException {
    final String fixtureRoot = System.getProperty("opennlp.wordnet.omwDir");
    Assumptions.assumeTrue(fixtureRoot != null && !fixtureRoot.isBlank(),
        "Run dev/test-omw-wordnets.sh to fetch and verify the pinned releases");

    final WnLmfResource resource =
        WnLmfReader.readResource(Path.of(fixtureRoot).resolve(relativeFile));
    assertEquals(1, resource.lexicons().size());
    final WnLmfLexicon lexicon = resource.lexicons().get(0);
    assertEquals(language, lexicon.language());
    assertEquals(List.of(new WnLmfDependency("omw-en", "2.0")),
        lexicon.dependencies());
    assertEquals(expectedSynset,
        lexicon.knowledgeBase().lookup(lemma, WordNetPOS.NOUN).get(0).id());
  }

  /**
   * Supplies the pinned OMW releases and representative noun lookups.
   *
   * @return The release arguments.
   */
  private static Stream<Arguments> wordnets() {
    return Stream.of(
        Arguments.of("it", "omw-it/omw-it.xml", "cane", "omw-it-02084071-n"),
        Arguments.of("es", "omw-es/omw-es.xml", "perro", "omw-es-02084071-n"),
        Arguments.of("sv", "omw-sv/omw-sv.xml", "hund", "omw-sv-02084071-n"));
  }
}
