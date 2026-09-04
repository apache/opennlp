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

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.WordNetPOS;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Smoke test against a complete Princeton WNDB release supplied by the developer. */
class WndbIntegrationTest {

  @Test
  void testCompleteRelease() throws IOException {
    final String fixtureRoot = System.getProperty("opennlp.wordnet.wndbDir");
    Assumptions.assumeTrue(fixtureRoot != null && !fixtureRoot.isBlank(),
        "Set opennlp.wordnet.wndbDir to a complete WNDB directory");

    final LexicalKnowledgeBase wordNet = WndbReader.read(Path.of(fixtureRoot));

    assertFalse(wordNet.lookup("dog", WordNetPOS.NOUN).isEmpty());
    assertFalse(wordNet.lookup("run", WordNetPOS.VERB).isEmpty());
    assertFalse(wordNet.lookup("quick", WordNetPOS.ADJECTIVE).isEmpty());
    assertFalse(wordNet.lookup("quickly", WordNetPOS.ADVERB).isEmpty());
  }
}
