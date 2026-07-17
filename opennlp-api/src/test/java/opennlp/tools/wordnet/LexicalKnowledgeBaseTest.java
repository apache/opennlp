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
package opennlp.tools.wordnet;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the {@link LexicalKnowledgeBase} default methods against a minimal in-memory
 * implementation, so the defaults are validated independently of any reader.
 */
public class LexicalKnowledgeBaseTest {

  private static final Synset DOG = new Synset("test-1-n", WordNetPOS.NOUN, List.of("dog"),
      "a domesticated canid", Map.of(WordNetRelation.HYPERNYM, List.of("test-2-n")));

  private static final Synset CANID = new Synset("test-2-n", WordNetPOS.NOUN, List.of("canid"),
      "a carnivorous mammal", Map.of(WordNetRelation.HYPONYM, List.of("test-1-n")));

  // A deliberately tiny implementation of only the two abstract methods.
  private static final LexicalKnowledgeBase LEXICON = new LexicalKnowledgeBase() {

    @Override
    public List<Synset> lookup(String lemma, WordNetPOS pos) {
      if (lemma == null) {
        throw new IllegalArgumentException("Lemma must not be null");
      }
      if (pos == null) {
        throw new IllegalArgumentException("Pos must not be null");
      }
      if (pos == WordNetPOS.NOUN && "dog".equals(lemma)) {
        return List.of(DOG);
      }
      return List.of();
    }

    @Override
    public Optional<Synset> synset(String synsetId) {
      if (synsetId == null) {
        throw new IllegalArgumentException("SynsetId must not be null");
      }
      if (DOG.id().equals(synsetId)) {
        return Optional.of(DOG);
      }
      if (CANID.id().equals(synsetId)) {
        return Optional.of(CANID);
      }
      return Optional.empty();
    }
  };

  @Test
  void testRelatedNavigatesThroughSynset() {
    assertEquals(List.of("test-2-n"), LEXICON.related("test-1-n", WordNetRelation.HYPERNYM));
    assertEquals(List.of("test-1-n"), LEXICON.related("test-2-n", WordNetRelation.HYPONYM));
  }

  @Test
  void testRelatedIsEmptyForAbsentRelationOrUnknownSynset() {
    assertTrue(LEXICON.related("test-1-n", WordNetRelation.ANTONYM).isEmpty());
    assertTrue(LEXICON.related("test-99-n", WordNetRelation.HYPERNYM).isEmpty());
  }

  @Test
  void testRelatedRejectsNulls() {
    assertThrows(IllegalArgumentException.class,
        () -> LEXICON.related(null, WordNetRelation.HYPERNYM));
    assertThrows(IllegalArgumentException.class, () -> LEXICON.related("test-1-n", null));
  }

  @Test
  void testContainsFollowsLookup() {
    assertTrue(LEXICON.contains("dog", WordNetPOS.NOUN));
    assertFalse(LEXICON.contains("dog", WordNetPOS.VERB));
    assertFalse(LEXICON.contains("cat", WordNetPOS.NOUN));
  }

  @Test
  void testContainsRejectsNulls() {
    assertThrows(IllegalArgumentException.class, () -> LEXICON.contains(null, WordNetPOS.NOUN));
    assertThrows(IllegalArgumentException.class, () -> LEXICON.contains("dog", null));
  }
}
