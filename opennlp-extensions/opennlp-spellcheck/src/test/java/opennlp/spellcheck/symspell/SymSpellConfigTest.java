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

package opennlp.spellcheck.symspell;

import org.junit.jupiter.api.Test;

import opennlp.spellcheck.distance.DamerauOSADistance;
import opennlp.spellcheck.distance.LevenshteinDistance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SymSpellConfigTest {

  @Test
  void defaultConfigMatchesReferenceDefaults() {
    final SymSpellConfig config = SymSpellConfig.defaultConfig();
    assertEquals(2, config.maxDictionaryEditDistance());
    assertEquals(7, config.prefixLength());
    assertEquals(1, config.countThreshold());
    assertSame(DamerauOSADistance.INSTANCE, config.editDistance());
    assertEquals(SymSpellConfig.DERIVE_CORPUS_WORD_COUNT, config.corpusWordCount(),
        "corpus word count defaults to derive-from-dictionary");
  }

  @Test
  void corpusWordCountCanBePinned() {
    final SymSpellConfig config = SymSpellConfig.builder().corpusWordCount(1_000_000L).build();
    assertEquals(1_000_000L, config.corpusWordCount());
  }

  @Test
  void negativeCorpusWordCountIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> SymSpellConfig.builder().corpusWordCount(-1));
  }

  @Test
  void builderOverridesAreApplied() {
    final SymSpellConfig config = SymSpellConfig.builder()
        .maxDictionaryEditDistance(3)
        .prefixLength(5)
        .countThreshold(10)
        .editDistance(LevenshteinDistance.INSTANCE)
        .build();
    assertEquals(3, config.maxDictionaryEditDistance());
    assertEquals(5, config.prefixLength());
    assertEquals(10, config.countThreshold());
    assertSame(LevenshteinDistance.INSTANCE, config.editDistance());
  }

  @Test
  void negativeMaxEditDistanceIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> SymSpellConfig.builder().maxDictionaryEditDistance(-1));
  }

  @Test
  void prefixLengthMustBeAtLeastOne() {
    assertThrows(IllegalArgumentException.class,
        () -> SymSpellConfig.builder().prefixLength(0));
  }

  @Test
  void countThresholdMustBeAtLeastOne() {
    assertThrows(IllegalArgumentException.class,
        () -> SymSpellConfig.builder().countThreshold(0));
  }

  @Test
  void nullEditDistanceIsRejected() {
    assertThrows(NullPointerException.class,
        () -> SymSpellConfig.builder().editDistance(null));
  }

  @Test
  void prefixLengthMustExceedMaxEditDistance() {
    // prefixLength (2) <= maxDictionaryEditDistance (2) is invalid.
    assertThrows(IllegalArgumentException.class,
        () -> SymSpellConfig.builder().maxDictionaryEditDistance(2).prefixLength(2).build());
  }
}
