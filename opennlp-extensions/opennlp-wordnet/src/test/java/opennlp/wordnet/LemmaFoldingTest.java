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

import java.util.List;

import org.junit.jupiter.api.Test;

import opennlp.tools.wordnet.WordNetPOS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the shared fold and split behavior every user of {@link LemmaFolding} depends on:
 * the exception lists, the sense index keys, and the WN-LMF members parsing all fold and
 * split through this one implementation.
 */
public class LemmaFoldingTest {

  @Test
  void testFoldLowercasesWithRootLocaleAndTreatsUnderscoreAsSpace() {
    assertEquals("mice", LemmaFolding.fold("MICE"));
    assertEquals("domestic dog", LemmaFolding.fold("Domestic_Dog"));
    assertEquals("attorney general", LemmaFolding.fold("attorney_general"));
    assertEquals("dog", LemmaFolding.fold("dog"));
    assertEquals("", LemmaFolding.fold(""));
  }

  @Test
  void testSplitOnSpacesCollapsesRunsAndIgnoresEdges() {
    assertEquals(List.of("a", "b", "c"), LemmaFolding.splitOnSpaces("a b c"));
    assertEquals(List.of("a", "b"), LemmaFolding.splitOnSpaces("a   b"));
    assertEquals(List.of("a"), LemmaFolding.splitOnSpaces("a"));
    assertEquals(List.of("a"), LemmaFolding.splitOnSpaces(" a "));
    assertEquals(List.of(), LemmaFolding.splitOnSpaces(""));
    assertEquals(List.of(), LemmaFolding.splitOnSpaces("   "));
  }

  @Test
  void testLemmaKeyAndExceptionLookupAgreeOnTheFold() {
    // The agreement that makes Morphy correct: a key built from a stored written form and a
    // query folded at lookup time land on the same canonical shape.
    assertEquals(InMemoryWordNetLexicon.LemmaKey.of("Domestic_Dog", WordNetPOS.NOUN),
        InMemoryWordNetLexicon.LemmaKey.of(LemmaFolding.fold("DOMESTIC_DOG"), WordNetPOS.NOUN));
  }

  @Test
  void testFoldRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> LemmaFolding.fold(null));
  }
}
