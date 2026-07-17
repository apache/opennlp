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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import opennlp.tools.wordnet.WordNetPOS;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MorphyLemmatizerTest {

  private static MorphyLemmatizer morphy() {
    return new MorphyLemmatizer(WndbReaderTest.fixture(), MorphyExceptionsTest.fixture());
  }

  private static String one(String token, String tag) {
    return morphy().lemmatize(new String[] {token}, new String[] {tag})[0];
  }

  @ParameterizedTest
  @CsvSource({
      // Irregular forms resolve through the exception lists.
      "mice, NN, mouse",
      "Mice, NNS, mouse",
      "men, NNS, man",
      "ran, VBD, run",
      "running, VBG, run",
      "went, VBD, go",
      "gone, VBN, go",
      "best, RBS, well",
      // Regular detachments, validated against the lexicon.
      "dogs, NNS, dog",
      "boxes, NNS, box",
      "berries, NNS, berry",
      "runs, NNS, run",
      "runs, VBZ, run",
      "walked, VBD, walk",
      "walking, VBG, walk",
      "walks, VBZ, walk",
      "moved, VBD, move",
      "taller, JJR, tall",
      "tallest, JJS, tall",
      "larger, JJR, large",
      // A word that is already a lemma comes back as itself.
      "dog, NN, dog",
      "quickly, RB, quickly",
      // WordNet letter tags are accepted alongside Penn tags.
      "dogs, n, dog",
      "walked, v, walk",
      "taller, a, tall",
      "best, r, well",
  })
  void testLemmatizesToken(String token, String tag, String lemma) {
    assertEquals(lemma, one(token, tag));
  }

  @ParameterizedTest
  @CsvSource({
      // Rule candidates not in the lexicon are rejected, not returned.
      "dogged, VBD",
      "boxes, VBZ",
      "glarbs, NNS",
      // A known word under the wrong part of speech is unknown.
      "walk, NN",
      // Tags outside the mapping yield the unknown marker.
      "dog, DT",
      "dog, XYZ",
      "dogs, ''",
      // Multi-letter closed-class tags that merely begin with a WordNet letter code are not
      // adjective lookups: AUX was must be unknown, and AUX taller must not detach to tall.
      "was, AUX",
      "taller, AUX",
  })
  void testUnknownYieldsMarker(String token, String tag) {
    assertEquals("O", one(token, tag));
  }

  @Test
  void testExceptionHitsAreReturnedWithoutLexiconValidation() {
    // oxen maps to ox, which the miniature lexicon does not contain; the exception list is
    // authoritative for irregulars, so the lemma is returned anyway.
    assertEquals("ox", one("oxen", "NNS"));
    // better maps to good, also absent from the miniature lexicon.
    assertEquals("good", one("better", "JJR"));
  }

  @Test
  void testArrayFormKeepsPositions() {
    final String[] lemmas = morphy().lemmatize(
        new String[] {"The", "mice", "ran", "quickly"},
        new String[] {"DT", "NNS", "VBD", "RB"});
    assertArrayEquals(new String[] {"O", "mouse", "run", "quickly"}, lemmas);
  }

  @Test
  void testListFormReturnsAllCandidates() {
    final List<List<String>> lemmas = morphy().lemmatize(
        List.of("glarbs", "berries"), List.of("NNS", "NNS"));
    assertEquals(List.of("O"), lemmas.get(0));
    assertEquals(List.of("berry"), lemmas.get(1));
  }

  @Test
  void testWorksIdenticallyOverTheWnLmfLexicon() {
    final MorphyLemmatizer lmfMorphy =
        new MorphyLemmatizer(WnLmfReaderTest.fixture(), MorphyExceptionsTest.fixture());
    assertArrayEquals(new String[] {"mouse", "box", "walk", "large", "O"},
        lmfMorphy.lemmatize(
            new String[] {"mice", "boxes", "walking", "larger", "dogged"},
            new String[] {"NNS", "NNS", "VBG", "JJR", "VBD"}));
  }

  @ParameterizedTest
  @CsvSource(nullValues = "none", value = {
      "NNP, NOUN",
      "VBZ, VERB",
      "JJ, ADJECTIVE",
      "RBR, ADVERB",
      "a, ADJECTIVE",
      "s, ADJECTIVE",
      "ADJ, ADJECTIVE",
      "ADV, ADVERB",
      "r, ADVERB",
      "DT, none",
      "'', none",
      // The letter codes a and s match only as one-letter tags: multi-letter tags beginning
      // with those letters are closed-class or symbol tags, never adjectives.
      "AUX, none",
      "ADP, none",
      "SCONJ, none",
      "SYM, none",
  })
  void testPosFromTagMapping(String tag, WordNetPOS pos) {
    assertEquals(pos, MorphyLemmatizer.posFromTag(tag));
  }

  @Test
  void testPosFromTagRejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> MorphyLemmatizer.posFromTag(null));
  }

  @Test
  void testConstructorFailsLoudWithoutInputs() {
    final MorphyExceptions exceptions = MorphyExceptionsTest.fixture();
    assertThrows(IllegalArgumentException.class,
        () -> new MorphyLemmatizer(null, exceptions));
    assertThrows(IllegalArgumentException.class,
        () -> new MorphyLemmatizer(WndbReaderTest.fixture(), null));
  }

  @Test
  void testRejectsNullOrMismatchedSequences() {
    final MorphyLemmatizer morphy = morphy();
    assertThrows(IllegalArgumentException.class,
        () -> morphy.lemmatize((String[]) null, new String[0]));
    assertThrows(IllegalArgumentException.class,
        () -> morphy.lemmatize(new String[0], (String[]) null));
    assertThrows(IllegalArgumentException.class,
        () -> morphy.lemmatize(new String[] {"a", "b"}, new String[] {"NN"}));
    assertThrows(IllegalArgumentException.class,
        () -> morphy.lemmatize(List.of("a"), List.of("NN", "NN")));
    assertThrows(IllegalArgumentException.class,
        () -> morphy.lemmatize(new String[] {null}, new String[] {"NN"}));
    assertThrows(IllegalArgumentException.class,
        () -> morphy.lemmatize(new String[] {"dog"}, new String[] {null}));
  }
}
