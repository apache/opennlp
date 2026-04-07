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

package opennlp.tools.tokenize;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link BPETokenizerTrainer} class.
 * <p>
 * Verifies that BPE merge operations are learned correctly from
 * a training corpus and that the resulting model can be used for tokenization.
 *
 * @see BPETokenizerTrainer
 * @see BPEModel
 */
public class BPETokenizerTrainerTest {

  private BPETokenizerTrainer trainer;

  @BeforeEach
  void setUp() {
    trainer = new BPETokenizerTrainer();
  }

  /**
   * Tests that training produces a non-null model with merge rules.
   */
  @Test
  void testTrainProducesModel() {
    final List<String> corpus = List.of(
        "low low low low low",
        "lower lower lower",
        "newest newest newest newest",
        "widest widest widest"
    );

    final BPEModel model = trainer.train(corpus, 10, "en");

    Assertions.assertNotNull(model);
    Assertions.assertFalse(model.getMerges().isEmpty());
    Assertions.assertTrue(model.getMerges().size() <= 10);
  }

  /**
   * Tests that the first merge is the most frequent adjacent pair.
   * For the corpus "ab ab ab ...", the most frequent pair is ("a", "b&lt;/w&gt;").
   */
  @Test
  void testFirstMergeIsMostFrequentPair() {
    final List<String> corpus = List.of(
        "ab ab ab ab ab ab ab ab ab ab"
    );

    final BPEModel model = trainer.train(corpus, 1, "en");

    Assertions.assertEquals(1, model.getMerges().size());
    Assertions.assertEquals("a", model.getMerges().getFirst().left());
    Assertions.assertEquals("b" + BPETokenizer.END_OF_WORD, model.getMerges().getFirst().right());
  }

  /**
   * Tests that requesting more merges than possible stops gracefully.
   */
  @Test
  void testMoreMergesThanPossible() {
    final List<String> corpus = List.of("ab");

    // "ab" has only one possible pair: ("a", "b</w>")
    final BPEModel model = trainer.train(corpus, 100, "en");

    // Should stop after exhausting all possible merges
    Assertions.assertTrue(model.getMerges().size() < 100);
    Assertions.assertFalse(model.getMerges().isEmpty());
  }

  /**
   * Tests that frequent words get merged into fewer tokens.
   */
  @Test
  void testFrequentWordsProduceFewerTokens() {
    final List<String> corpus = List.of(
        "the the the the the the the the the the",
        "the the the the the the the the the the",
        "xyzzy"
    );

    final BPEModel model = trainer.train(corpus, 20, "en");
    final BPETokenizer tokenizer = new BPETokenizer(model);

    final String[] theTokens = tokenizer.tokenize("the");
    final String[] xyzzyTokens = tokenizer.tokenize("xyzzy");

    // "the" (very frequent) should have fewer or equal tokens compared to "xyzzy" (rare)
    Assertions.assertTrue(theTokens.length <= xyzzyTokens.length,
        "Expected 'the' (" + Arrays.toString(theTokens) + ") to have fewer tokens than 'xyzzy' ("
            + Arrays.toString(xyzzyTokens) + ")");
  }

  /**
   * Tests that the trained model produces a tokenizer that reconstructs
   * the original words when tokens are concatenated.
   */
  @Test
  void testTrainAndTokenizeRoundtrip() {
    final List<String> corpus = List.of(
        "the cat sat on the mat",
        "the cat sat on the mat",
        "the cat sat on the mat",
        "the dog sat on the log",
        "the dog sat on the log"
    );

    final BPEModel model = trainer.train(corpus, 20, "en");
    final BPETokenizer tokenizer = new BPETokenizer(model);

    // Verify token concatenation restores the original word
    for (final String word : new String[]{"the", "cat", "sat", "dog"}) {
      final String[] tokens = tokenizer.tokenize(word);
      Assertions.assertEquals(word, String.join("", tokens),
          "Token concatenation should reconstruct '" + word + "'");
    }
  }

  /**
   * Tests that training with an empty corpus produces a model with no merges.
   */
  @Test
  void testEmptyCorpus() {
    final BPEModel model = trainer.train(List.of(), 10, "en");

    Assertions.assertNotNull(model);
    Assertions.assertTrue(model.getMerges().isEmpty());
  }

  /**
   * Tests that the language code is set on the produced model.
   */
  @Test
  void testLanguageCodePreserved() {
    final BPEModel model = trainer.train(List.of("hello world"), 5, "de");

    Assertions.assertEquals("de", model.getLanguage());
  }

  @Test
  void testNullCorpusThrows() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> trainer.train(null, 10, "en"));
  }

  @Test
  void testNullLanguageThrows() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> trainer.train(List.of("hello"), 10, null));
  }

  @Test
  void testZeroMergesThrows() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> trainer.train(List.of("hello"), 0, "en"));
  }

  @Test
  void testNegativeMergesThrows() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> trainer.train(List.of("hello"), -1, "en"));
  }
}
