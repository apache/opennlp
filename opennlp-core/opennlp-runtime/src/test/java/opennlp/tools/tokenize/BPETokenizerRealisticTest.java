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
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.Span;

/**
 * Integration tests for the BPE tokenization pipeline.
 * <p>
 * This test trains a BPE tokenizer from a realistic English corpus,
 * serializes and deserializes the model, then verifies tokenization
 * behavior end-to-end. Mirrors the structure of {@link TokenizerMETest}.
 *
 * @see BPETokenizer
 * @see BPETokenizerTrainer
 * @see BPEModel
 */
public class BPETokenizerRealisticTest {

  /**
   * A small but realistic training corpus for BPE.
   */
  private static final List<String> TRAINING_CORPUS = List.of(
      "Last September I tried to find out the address of an old school friend",
      "whom I had not seen for 15 years",
      "I just knew his name Alan McKennedy and I had heard the rumour",
      "that he had moved to Scotland the country of his ancestors",
      "So I called Julie a friend who is still in contact with him",
      "She told me that he lived in Edinburgh Worcesterstreet 12",
      "I wrote him a letter right away and he answered soon",
      "sounding very happy and delighted",
      "Last year I wanted to write a letter to my grandaunt",
      "Her 86th birthday was on October 6 and I no longer wanted",
      "to be hesitant to get in touch with her",
      "I did not know her face to face and so it was not easy",
      "for me to find out her address",
      "As she had two apartments in different countries",
      "I decided to write to both",
      "The first was in Paris in Rue de Grandes Illusions 5",
      "But Marie Clara as my aunt is called preferred her apartment in Berlin",
      "She lived there in beautiful Kaiserstrasse 13 particularly in summer",
      "Hi my name is Michael Graf how much is a taxi",
      "from Ostbahnhof to Hauptbahnhof",
      "About 10 Euro I reckon",
      "That sounds good",
      "So please call a driver to Leonardstrasse 112 near the Ostbahnhof",
      "I would like to be at Silberhornstrasse 12 as soon as possible",
      "Thank you very much"
  );

  private static BPEModel trainedModel;

  @BeforeAll
  static void setUpClass() {
    trainedModel = new BPETokenizerTrainer().train(TRAINING_CORPUS, 100, "en");
  }

  /**
   * Tests basic tokenization of a simple sentence with the trained model.
   * All words appear in the training corpus and should be fully merged.
   */
  @Test
  void testTokenizerSimpleModel() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);
    final String text = "I wrote a letter";

    final String[] tokens = tokenizer.tokenize(text);
    final Span[] spans = tokenizer.tokenizePos(text);

    // All four words are common in training corpus — assert exact reconstruction
    final String[] words = reconstructWords(tokens, spans, text);
    Assertions.assertArrayEquals(new String[] {"I", "wrote", "a", "letter"}, words);
  }

  /**
   * Tests tokenization of a sentence with words seen during training.
   * Frequent words should be tokenized into fewer subword pieces.
   */
  @Test
  void testFrequentWordsTokenizeEfficiently() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);

    // "the" and "in" appear very frequently in the training corpus
    final String[] theTokens = tokenizer.tokenize("the");
    final String[] inTokens = tokenizer.tokenize("in");

    // With 100 merges on this corpus, these common words should be single tokens
    Assertions.assertEquals(1, theTokens.length, "Expected 'the' as single token");
    Assertions.assertEquals("the", theTokens[0]);
    Assertions.assertEquals(1, inTokens.length, "Expected 'in' as single token");
    Assertions.assertEquals("in", inTokens[0]);
  }

  /**
   * Tests tokenization of unseen words — they should be split into subword pieces
   * but concatenation must still reconstruct the original.
   */
  @Test
  void testUnseenWordsTokenization() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);

    final String[] tokens = tokenizer.tokenize("unbelievable");

    // An unseen word will be split into multiple subword pieces
    Assertions.assertTrue(tokens.length > 1,
        "Unseen word 'unbelievable' should be split into multiple subword tokens");
    Assertions.assertEquals("unbelievable", String.join("", tokens),
        "Concatenation of subword tokens must reconstruct the original word");
  }

  /**
   * Tests that tokenizePos spans cover the full input text without gaps or overlaps
   * and that reconstructed words match the original sentence.
   */
  @Test
  void testTokenizePosSpanCoverage() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);
    final String text = "She lived in Edinburgh";
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
    Assertions.assertArrayEquals(new String[] {"She", "lived", "in", "Edinburgh"}, words);
  }

  /**
   * Tests that the BPE tokenizer handles multi-word input correctly,
   * similar to {@link TokenizerMETest#testTokenizer()}.
   */
  @Test
  void testTokenizer() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);
    final String sentence = "I had not seen him for years";
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
   * Similar to {@link TokenizerModelTest#testTokenizerModelSerialization()}.
   */
  @Test
  void testTrainSerializeDeserializeTokenize() throws IOException {
    // Serialize
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    trainedModel.serialize(out);
    out.close();

    // Deserialize
    final BPEModel loaded = new BPEModel(new ByteArrayInputStream(out.toByteArray()));

    // Tokenize with both original and deserialized model — results should match
    final BPETokenizer original = new BPETokenizer(trainedModel);
    final BPETokenizer restored = new BPETokenizer(loaded);

    final String sentence = "I wrote him a letter right away";
    Assertions.assertArrayEquals(
        original.tokenize(sentence),
        restored.tokenize(sentence));
  }

  /**
   * Tests that the BPE tokenizer fulfills the {@link Tokenizer} contract:
   * tokenize() and tokenizePos() must be consistent.
   */
  @Test
  void testTokenizeAndTokenizePosConsistency() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);
    final String text = "She told me that he lived in Edinburgh";

    final String[] tokens = tokenizer.tokenize(text);
    final Span[] spans = tokenizer.tokenizePos(text);

    Assertions.assertEquals(tokens.length, spans.length);

    for (int i = 0; i < tokens.length; i++) {
      Assertions.assertEquals(tokens[i], spans[i].getCoveredText(text).toString(),
          "Token at index " + i + " should match span-covered text");
    }
  }

  /**
   * Tests tokenization with a model trained on a German corpus.
   * Frequent German words should be fully merged and reconstructed correctly.
   */
  @Test
  void testTrainWithDifferentLanguage() {
    final List<String> germanCorpus = List.of(
        "Ich wähle den auf Seite 183 mitgeteilten Traum",
        "von der botanischen Monographie",
        "Der Traum von der botanischen Monographie",
        "Ich wähle den Traum von der botanischen Monographie"
    );

    final BPEModel model = new BPETokenizerTrainer().train(germanCorpus, 50, "de");
    Assertions.assertEquals("de", model.getLanguage());

    final BPETokenizer tokenizer = new BPETokenizer(model);
    final String text = "der botanischen Monographie";
    final String[] tokens = tokenizer.tokenize(text);
    final Span[] spans = tokenizer.tokenizePos(text);

    // Assert words are reconstructed correctly
    final String[] words = reconstructWords(tokens, spans, text);
    Assertions.assertArrayEquals(new String[] {"der", "botanischen", "Monographie"}, words);
  }

  /**
   * Tests that the BPE tokenizer handles punctuation mixed with words.
   * BPE treats punctuation as characters — they stay attached to the word
   * since BPE splits on whitespace first.
   */
  @Test
  void testPunctuationHandling() {
    final BPETokenizer tokenizer = new BPETokenizer(trainedModel);
    final String text = "Hello, world!";

    final String[] tokens = tokenizer.tokenize(text);
    final Span[] spans = tokenizer.tokenizePos(text);

    // "Hello," and "world!" are separate whitespace tokens, each may be subword-split
    final String[] words = reconstructWords(tokens, spans, text);
    Assertions.assertEquals(2, words.length);
    Assertions.assertEquals("Hello,", words[0]);
    Assertions.assertEquals("world!", words[1]);
  }

  /**
   * Tests that training with a larger number of merges produces
   * coarser tokenization (fewer subword tokens per word).
   */
  @Test
  void testMoreMergesProducesCoarserTokens() {
    final BPEModel fewMerges = new BPETokenizerTrainer().train(TRAINING_CORPUS, 5, "en");
    final BPEModel manyMerges = new BPETokenizerTrainer().train(TRAINING_CORPUS, 100, "en");

    final BPETokenizer fewTokenizer = new BPETokenizer(fewMerges);
    final BPETokenizer manyTokenizer = new BPETokenizer(manyMerges);

    // With more merges, the same text should produce fewer or equal tokens
    final String text = "I wanted to write a letter to my grandaunt";
    final int fewCount = fewTokenizer.tokenize(text).length;
    final int manyCount = manyTokenizer.tokenize(text).length;

    Assertions.assertTrue(manyCount <= fewCount,
        "More merges (" + manyCount + " tokens) should produce fewer or equal tokens "
            + "than fewer merges (" + fewCount + " tokens)");
  }

  /**
   * Reconstructs whitespace-separated words from subword tokens using span positions.
   */
  private String[] reconstructWords(String[] tokens, Span[] spans, String text) {
    final java.util.List<String> words = new java.util.ArrayList<>();
    final StringBuilder currentWord = new StringBuilder();
    int lastWordEnd = -1;

    for (final Span span : spans) {
      if (lastWordEnd >= 0 && span.getStart() > lastWordEnd) {
        // Gap between spans means a whitespace boundary — new word
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
