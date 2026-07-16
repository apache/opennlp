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

package opennlp.tools.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import opennlp.tools.postag.POSTagger;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Walks through the document pipeline the way a first-time user would: wrap existing
 * analysis components in their adapter annotators, add one custom annotator, build a
 * {@link DocumentAnalyzer}, analyze a two-sentence text, and read every layer back with
 * its spans in original text coordinates.
 *
 * <p>The wrapped components are tiny deterministic stand-ins defined in this class, so
 * every expected span and value below follows directly from the input text. The point
 * under demonstration is how the layers connect, not the quality of any single step.</p>
 */
public class DocumentPipelineExampleTest {

  /**
   * The key of the custom layer produced by {@link TokenLengthAnnotator}. Any producer
   * may introduce such a key in its own code; the container needs no change for it.
   */
  private static final LayerKey<Integer> TOKEN_LENGTHS =
      LayerKey.of("token-lengths", Integer.class);

  /**
   * A deterministic sentence detector that ends a sentence after every period and
   * expects a single space between sentences. Only the span-producing method is
   * implemented because the adapter calls no other method.
   */
  private static final SentenceDetector PERIOD_SPLITTER = new SentenceDetector() {

    @Override
    public String[] sentDetect(CharSequence s) {
      throw new UnsupportedOperationException("the adapter only calls sentPosDetect");
    }

    @Override
    public Span[] sentPosDetect(CharSequence s) {
      final String text = s.toString();
      final List<Span> spans = new ArrayList<>();
      int start = 0;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '.') {
          spans.add(new Span(start, i + 1));
          start = i + 2;
        }
      }
      return spans.toArray(new Span[0]);
    }
  };

  /**
   * A deterministic tokenizer that splits on single space characters and keeps all
   * other characters, including sentence-final periods, attached to their token. Only
   * the span-producing method is implemented because the adapter calls no other method.
   */
  private static final Tokenizer SPACE_TOKENIZER = new Tokenizer() {

    @Override
    public String[] tokenize(String s) {
      throw new UnsupportedOperationException("the adapter only calls tokenizePos");
    }

    @Override
    public Span[] tokenizePos(String s) {
      final List<Span> spans = new ArrayList<>();
      int start = -1;
      for (int i = 0; i <= s.length(); i++) {
        final boolean boundary = i == s.length() || s.charAt(i) == ' ';
        if (boundary && start >= 0) {
          spans.add(new Span(start, i));
          start = -1;
        } else if (!boundary && start < 0) {
          start = i;
        }
      }
      return spans.toArray(new Span[0]);
    }
  };

  /**
   * A deterministic tagger backed by a fixed dictionary covering exactly the tokens of
   * the example text. An unknown token fails the test immediately rather than receiving
   * a silent fallback tag.
   */
  private static final POSTagger DICTIONARY_TAGGER = new POSTagger() {

    private final Map<String, String> tagsByToken = Map.of(
        "The", "DT", "dog", "NN", "barks.", "VBZ", "It", "PRP", "naps.", "VBZ");

    @Override
    public String[] tag(String[] sentence) {
      final String[] tags = new String[sentence.length];
      for (int i = 0; i < sentence.length; i++) {
        final String tag = tagsByToken.get(sentence[i]);
        if (tag == null) {
          throw new IllegalArgumentException("no tag defined for token: " + sentence[i]);
        }
        tags[i] = tag;
      }
      return tags;
    }

    @Override
    public String[] tag(String[] sentence, Object[] additionalContext) {
      return tag(sentence);
    }

    @Override
    public Sequence[] topKSequences(String[] sentence) {
      throw new UnsupportedOperationException("the adapter only calls tag");
    }

    @Override
    public Sequence[] topKSequences(String[] sentence, Object[] additionalContext) {
      throw new UnsupportedOperationException("the adapter only calls tag");
    }
  };

  /**
   * A custom pipeline step written directly against {@link DocumentAnnotator}: it reads
   * the token layer and provides {@link #TOKEN_LENGTHS}, one annotation per token on the
   * token's span, whose value is the character length of the token text.
   */
  private static final class TokenLengthAnnotator implements DocumentAnnotator {

    /**
     * Adds the {@link #TOKEN_LENGTHS} layer computed from {@link Layers#TOKENS}.
     *
     * @param document The document to annotate. Must not be {@code null} and must
     *                 contain the token layer.
     * @return A new {@link Document} carrying the token length layer. Never {@code null}.
     * @throws IllegalArgumentException Thrown if {@code document} is {@code null} or the
     *         token layer is absent.
     */
    @Override
    public Document annotate(Document document) {
      if (document == null) {
        throw new IllegalArgumentException("document must not be null");
      }
      final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
      if (tokens.isEmpty()) {
        throw new IllegalArgumentException("document lacks the required layer "
            + Layers.TOKENS);
      }
      final List<Annotation<Integer>> lengths = new ArrayList<>(tokens.size());
      for (final Annotation<String> token : tokens) {
        lengths.add(new Annotation<>(token.span(), token.value().length()));
      }
      return document.with(TOKEN_LENGTHS, lengths);
    }

    @Override
    public Set<LayerKey<?>> requires() {
      return Set.of(Layers.TOKENS);
    }

    @Override
    public Set<LayerKey<?>> provides() {
      return Set.of(TOKEN_LENGTHS);
    }
  }

  /**
   * Runs the full pipeline story: sentences, tokens, part-of-speech tags, and one custom
   * layer over a two-sentence text, then verifies every annotation of every layer, span
   * by span, in original text coordinates.
   */
  @Test
  void testFullPipelineStory() {
    final DocumentAnalyzer analyzer = DocumentAnalyzer.builder()
        .add(new SentenceDetectorAnnotator(PERIOD_SPLITTER))
        .add(new TokenizerAnnotator(SPACE_TOKENIZER))
        .add(new POSTaggerAnnotator(DICTIONARY_TAGGER))
        .add(new TokenLengthAnnotator())
        .build();

    final Document document = analyzer.analyze("The dog barks. It naps.");

    // The document carries exactly the four layers the pipeline provides.
    assertEquals(Set.of(Layers.SENTENCES, Layers.TOKENS, Layers.POS_TAGS, TOKEN_LENGTHS),
        document.layers());
    assertEquals("The dog barks. It naps.", document.text());

    // Sentence layer: one annotation per sentence, covering it in document coordinates.
    final List<Annotation<String>> sentences = document.get(Layers.SENTENCES);
    assertEquals(2, sentences.size());
    assertEquals(new Span(0, 14), sentences.get(0).span());
    assertEquals("The dog barks.", sentences.get(0).value());
    assertEquals(new Span(15, 23), sentences.get(1).span());
    assertEquals("It naps.", sentences.get(1).value());

    // Token layer: five tokens; the second sentence's spans are shifted back to
    // document coordinates, so every span can index into the original text.
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    assertEquals(5, tokens.size());
    assertEquals(new Span(0, 3), tokens.get(0).span());
    assertEquals("The", tokens.get(0).value());
    assertEquals(new Span(4, 7), tokens.get(1).span());
    assertEquals("dog", tokens.get(1).value());
    assertEquals(new Span(8, 14), tokens.get(2).span());
    assertEquals("barks.", tokens.get(2).value());
    assertEquals(new Span(15, 17), tokens.get(3).span());
    assertEquals("It", tokens.get(3).value());
    assertEquals(new Span(18, 23), tokens.get(4).span());
    assertEquals("naps.", tokens.get(4).value());

    // Tag layer: aligned with the token layer by position, each tag on its token's span.
    final List<Annotation<String>> tags = document.get(Layers.POS_TAGS);
    assertEquals(5, tags.size());
    assertEquals("DT", tags.get(0).value());
    assertEquals("NN", tags.get(1).value());
    assertEquals("VBZ", tags.get(2).value());
    assertEquals("PRP", tags.get(3).value());
    assertEquals("VBZ", tags.get(4).value());
    for (int i = 0; i < tags.size(); i++) {
      assertEquals(tokens.get(i).span(), tags.get(i).span());
    }

    // Custom layer: the container returns it as List<Annotation<Integer>>, so the
    // values are used as numbers without a cast.
    final List<Annotation<Integer>> lengths = document.get(TOKEN_LENGTHS);
    assertEquals(5, lengths.size());
    assertEquals(3, lengths.get(0).value());
    assertEquals(3, lengths.get(1).value());
    assertEquals(6, lengths.get(2).value());
    assertEquals(2, lengths.get(3).value());
    assertEquals(5, lengths.get(4).value());
    for (int i = 0; i < lengths.size(); i++) {
      assertEquals(tokens.get(i).span(), lengths.get(i).span());
    }

    // Every span refers to the original text, so covered text always round-trips.
    for (final Annotation<String> token : tokens) {
      assertEquals(token.value(),
          token.span().getCoveredText(document.text()).toString());
    }
  }

  /**
   * Verifies that the analyzer leaves the input untouched between calls: analyzing two
   * texts with the same analyzer yields two independent documents.
   */
  @Test
  void testAnalyzerIsReusableAcrossTexts() {
    final DocumentAnalyzer analyzer = DocumentAnalyzer.builder()
        .add(new SentenceDetectorAnnotator(PERIOD_SPLITTER))
        .add(new TokenizerAnnotator(SPACE_TOKENIZER))
        .build();

    final Document first = analyzer.analyze("The dog barks.");
    final Document second = analyzer.analyze("It naps.");

    assertEquals(3, first.get(Layers.TOKENS).size());
    assertEquals(2, second.get(Layers.TOKENS).size());
    assertEquals("The dog barks.", first.text());
    assertEquals("It naps.", second.text());
    assertEquals(1, first.get(Layers.SENTENCES).size());
    assertEquals(1, second.get(Layers.SENTENCES).size());
  }
}
