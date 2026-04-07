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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import opennlp.tools.util.Span;

/**
 * Abstract base class for realistic BPE tokenization integration tests.
 * <p>
 * Subclasses provide language-specific training corpora and test inputs.
 * This class contains all common test methods that exercise the BPE pipeline
 * end-to-end: training, tokenization, serialization, and consistency checks.
 *
 * @see BPETokenizer
 * @see BPETokenizerTrainer
 * @see BPEModel
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractBPETokenizerRealisticTest {

  private BPEModel trainedModel;

  // --- Abstract methods for language-specific data ---

  /**
   * Returns a realistic training corpus for the target language.
   */
  abstract List<String> getTrainingCorpus();

  /**
   * Returns the ISO language code (e.g., "en", "de", "fr").
   */
  abstract String getLanguageCode();

  /**
   * Returns the number of BPE merges to use during training. Default is 100.
   */
  int getNumMerges() {
    return 100;
  }

  /**
   * Returns a simple sentence whose words all appear in the training corpus.
   */
  abstract String getSimpleSentence();

  /**
   * Returns the expected words for {@link #getSimpleSentence()}.
   */
  abstract String[] getSimpleSentenceExpectedWords();

  /**
   * Returns a list of words expected to be single tokens after training.
   */
  abstract List<String> getFrequentWords();

  /**
   * Returns a word not seen in the training corpus.
   */
  abstract String getUnseenWord();

  /**
   * Returns a sentence for span coverage testing.
   */
  abstract String getSpanTestSentence();

  /**
   * Returns the expected words for {@link #getSpanTestSentence()}.
   */
  abstract String[] getSpanTestExpectedWords();

  /**
   * Returns a multi-word sentence for general tokenization testing.
   */
  abstract String getMultiWordSentence();

  /**
   * Returns a sentence for serialization roundtrip testing.
   */
  abstract String getSerializationTestSentence();

  /**
   * Returns a sentence for consistency testing between tokenize() and tokenizePos().
   */
  abstract String getConsistencyTestSentence();

  /**
   * Returns a sentence containing punctuation for testing.
   */
  abstract String getPunctuationTestSentence();

  /**
   * Returns the expected words (whitespace-delimited, punctuation attached)
   * for {@link #getPunctuationTestSentence()}.
   */
  abstract String[] getExpectedPunctuationWords();

  /**
   * Returns a sentence for testing that more merges produce coarser tokens.
   */
  abstract String getCoarseTokenizationSentence();

  @BeforeAll
  void setUpClass() {
    trainedModel = new BPETokenizerTrainer().train(
        getTrainingCorpus(), getNumMerges(), getLanguageCode());
  }

  /**
   * Tests basic tokenization of a simple sentence with the trained model.
   * All words appear in the training corpus and should be fully merged.
   */
  @Test
  void testTokenizerSimpleModel() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);
    final String text = getSimpleSentence();

    final String[] tokens = tokenizer.tokenize(text);
    final Span[] spans = tokenizer.tokenizePos(text);

    final String[] words = reconstructWords(tokens, spans, text);
    Assertions.assertArrayEquals(getSimpleSentenceExpectedWords(), words);
  }

  /**
   * Tests tokenization of frequent words seen during training.
   * Frequent words should be tokenized into single tokens.
   */
  @Test
  void testFrequentWordsTokenizeEfficiently() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);

    for (final String word : getFrequentWords()) {
      final String[] tokens = tokenizer.tokenize(word);
      Assertions.assertEquals(1, tokens.length,
          "Expected '" + word + "' as single token");
      Assertions.assertEquals(word, tokens[0]);
    }
  }

  /**
   * Tests tokenization of unseen words -- they should be split into subword pieces
   * but concatenation must still reconstruct the original.
   */
  @Test
  void testUnseenWordsTokenization() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);
    final String unseen = getUnseenWord();

    final String[] tokens = tokenizer.tokenize(unseen);

    Assertions.assertTrue(tokens.length > 1,
        "Unseen word '" + unseen + "' should be split into multiple subword tokens");
    Assertions.assertEquals(unseen, String.join("", tokens),
        "Concatenation of subword tokens must reconstruct the original word");
  }

  /**
   * Tests that tokenizePos spans cover the full input text without gaps or overlaps
   * and that reconstructed words match the original sentence.
   */
  @Test
  void testTokenizePosSpanCoverage() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);
    final String text = getSpanTestSentence();
    final String[] tokens = tokenizer.tokenize(text);
    final Span[] spans = tokenizer.tokenizePos(text);

    // Verify all spans extract non-empty substrings
    for (final Span span : spans) {
      final CharSequence covered = span.getCoveredText(text);
      Assertions.assertNotNull(covered);
      Assertions.assertFalse(covered.toString().isEmpty());
    }

    // Verify that spans + whitespace fully reconstruct the original text
    final StringBuilder sb = new StringBuilder();
    int lastEnd = 0;
    for (final Span span : spans) {
      if (span.getStart() > lastEnd) {
        sb.append(text, lastEnd, span.getStart());
      }
      sb.append(span.getCoveredText(text));
      lastEnd = span.getEnd();
    }
    Assertions.assertEquals(text, sb.toString());

    // Verify reconstructed words match expected
    final String[] words = reconstructWords(tokens, spans, text);
    Assertions.assertArrayEquals(getSpanTestExpectedWords(), words);
  }

  /**
   * Tests that the BPE tokenizer handles multi-word input correctly.
   */
  @Test
  void testTokenizer() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);
    final String sentence = getMultiWordSentence();
    final String[] tokens = tokenizer.tokenize(sentence);

    // Each word produces at least one token
    final String[] words = sentence.split(" ");
    Assertions.assertTrue(tokens.length >= words.length);

    // Reconstruct each word from its subword tokens via spans
    final Span[] spans = tokenizer.tokenizePos(sentence);
    final String[] reconstructed = reconstructWords(tokens, spans, sentence);
    Assertions.assertArrayEquals(words, reconstructed);
  }

  /**
   * Tests the full pipeline: train, serialize, deserialize, tokenize.
   */
  @Test
  void testTrainSerializeDeserializeTokenize() throws IOException {
    // Serialize
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      trainedModel.serialize(out);

      // Deserialize
      final BPEModel loaded = new BPEModel(new ByteArrayInputStream(out.toByteArray()));

      // Tokenize with both original and deserialized model -- results should match
      final BPETokenizer original = new BPETokenizer(trainedModel);
      final BPETokenizer restored = new BPETokenizer(loaded);

      final String sentence = getSerializationTestSentence();
      Assertions.assertArrayEquals(
          original.tokenize(sentence),
          restored.tokenize(sentence));
    }
  }

  /**
   * Tests that the BPE tokenizer fulfills the {@link Tokenizer} contract:
   * tokenize() and tokenizePos() must be consistent.
   */
  @Test
  void testTokenizeAndTokenizePosConsistency() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);
    final String text = getConsistencyTestSentence();

    final String[] tokens = tokenizer.tokenize(text);
    final Span[] spans = tokenizer.tokenizePos(text);

    Assertions.assertEquals(tokens.length, spans.length);

    for (int i = 0; i < tokens.length; i++) {
      Assertions.assertEquals(tokens[i], spans[i].getCoveredText(text).toString(),
          "Token at index " + i + " should match span-covered text");
    }
  }

  /**
   * Tests that the BPE tokenizer handles punctuation mixed with words.
   * BPE treats punctuation as characters -- they stay attached to the word
   * since BPE splits on whitespace first.
   */
  @Test
  void testPunctuationHandling() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);
    final String text = getPunctuationTestSentence();
    final String[] expectedWords = getExpectedPunctuationWords();

    final String[] tokens = tokenizer.tokenize(text);
    final Span[] spans = tokenizer.tokenizePos(text);

    final String[] words = reconstructWords(tokens, spans, text);
    Assertions.assertEquals(expectedWords.length, words.length);
    Assertions.assertArrayEquals(expectedWords, words);
  }

  /**
   * Tests that training with a larger number of merges produces
   * coarser tokenization (fewer subword tokens per word).
   */
  @Test
  void testMoreMergesProducesCoarserTokens() {
    final List<String> corpus = getTrainingCorpus();
    final String lang = getLanguageCode();

    final BPEModel fewMerges = new BPETokenizerTrainer().train(corpus, 5, lang);
    final BPEModel manyMerges = new BPETokenizerTrainer().train(corpus, 100, lang);

    final BPETokenizer fewTokenizer = new BPETokenizer(fewMerges);
    final BPETokenizer manyTokenizer = new BPETokenizer(manyMerges);

    final String text = getCoarseTokenizationSentence();
    final int fewCount = fewTokenizer.tokenize(text).length;
    final int manyCount = manyTokenizer.tokenize(text).length;

    Assertions.assertTrue(manyCount <= fewCount,
        "More merges (" + manyCount + " tokens) should produce fewer or equal tokens "
            + "than fewer merges (" + fewCount + " tokens)");
  }

  /**
   * Reconstructs whitespace-separated words from subword tokens using span positions.
   */
  String[] reconstructWords(String[] tokens, Span[] spans, String text) {
    final List<String> words = new ArrayList<>();
    final StringBuilder currentWord = new StringBuilder();
    int lastWordEnd = -1;

    for (final Span span : spans) {
      if (lastWordEnd >= 0 && span.getStart() > lastWordEnd) {
        // Gap between spans means a whitespace boundary -- new word
        words.add(currentWord.toString());
        currentWord.setLength(0);
      }
      currentWord.append(span.getCoveredText(text));
      lastWordEnd = span.getEnd();
    }
    if (!currentWord.isEmpty()) {
      words.add(currentWord.toString());
    }

    return words.toArray(new String[0]);
  }
}
