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

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import opennlp.tools.postag.POSTagger;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the {@link DocumentAnalyzer} pipeline over the adapter annotators, using simple
 * inline implementations of the task interfaces: whitespace tokenization, period sentence
 * splitting, and a dictionary tagger. The point under test is the pipeline mechanics and
 * span arithmetic, not model quality.
 */
public class DocumentAnalyzerTest {

  private static final SentenceDetector SPLITTER = new SentenceDetector() {

    @Override
    public String[] sentDetect(CharSequence s) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Span[] sentPosDetect(CharSequence s) {
      // split after each period; keep it simple for the test
      final String text = s.toString();
      int start = 0;
      final java.util.List<Span> spans = new java.util.ArrayList<>();
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '.') {
          spans.add(new Span(start, i + 1));
          start = i + 2;
        }
      }
      return spans.toArray(new Span[0]);
    }
  };

  private static final Tokenizer WHITESPACE = new Tokenizer() {

    @Override
    public String[] tokenize(String s) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Span[] tokenizePos(String s) {
      final java.util.List<Span> spans = new java.util.ArrayList<>();
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

  private static final POSTagger TAGGER = new POSTagger() {

    @Override
    public String[] tag(String[] sentence) {
      final String[] tags = new String[sentence.length];
      for (int i = 0; i < sentence.length; i++) {
        tags[i] = "barks.".contains(sentence[i]) ? "VBZ" : "X";
      }
      return tags;
    }

    @Override
    public String[] tag(String[] sentence, Object[] additionalContext) {
      return tag(sentence);
    }

    @Override
    public opennlp.tools.util.Sequence[] topKSequences(String[] sentence) {
      throw new UnsupportedOperationException();
    }

    @Override
    public opennlp.tools.util.Sequence[] topKSequences(String[] sentence,
        Object[] additionalContext) {
      throw new UnsupportedOperationException();
    }
  };

  @Test
  void testPipelineProducesAlignedLayersInOriginalCoordinates() {
    final Document document = DocumentAnalyzer.builder()
        .add(new SentenceDetectorAnnotator(SPLITTER))
        .add(new TokenizerAnnotator(WHITESPACE))
        .add(new POSTaggerAnnotator(TAGGER))
        .build()
        .analyze("the dog barks. she eats.");

    final List<Annotation<String>> sentences = document.get(Layers.SENTENCES);
    assertEquals(2, sentences.size());
    assertEquals("she eats.", sentences.get(1).value());

    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    assertEquals(5, tokens.size());
    // token of the second sentence, span in document coordinates
    assertEquals("she", tokens.get(3).value());
    assertEquals(new Span(15, 18), tokens.get(3).span());

    final List<Annotation<String>> tags = document.get(Layers.POS_TAGS);
    assertEquals(5, tags.size());
    assertEquals("VBZ", tags.get(2).value());
    assertEquals(tokens.get(2).span(), tags.get(2).span());
  }

  @Test
  void testTokenizerWorksWithoutSentences() {
    final Document document = DocumentAnalyzer.builder()
        .add(new TokenizerAnnotator(WHITESPACE))
        .build()
        .analyze("the dog");
    assertEquals(2, document.get(Layers.TOKENS).size());
  }

  @Test
  void testMisorderedPipelineFailsAtBuildTime() {
    final DocumentAnalyzer.Builder builder = DocumentAnalyzer.builder()
        .add(new POSTaggerAnnotator(TAGGER));
    assertThrows(IllegalArgumentException.class, builder::build);
  }

  @Test
  void testEmptyPipelineThrows() {
    assertThrows(IllegalArgumentException.class, () -> DocumentAnalyzer.builder().build());
  }

  @Test
  void testCustomLayerNeedsNoContainerChange() {
    // the additive claim: a brand-new layer type works without touching the container
    record Sentiment(String polarity, double score) {
    }
    final LayerKey<Sentiment> sentiment = LayerKey.of("sentiment", Sentiment.class);
    final DocumentAnnotator annotator = new DocumentAnnotator() {

      @Override
      public Document annotate(Document document) {
        final Span all = new Span(0, document.text().length());
        return document.with(sentiment,
            List.of(new Annotation<>(all, new Sentiment("positive", 0.9d))));
      }

      @Override
      public Set<LayerKey<?>> provides() {
        return Set.of(sentiment);
      }
    };
    final Document document = DocumentAnalyzer.builder().add(annotator).build()
        .analyze("good dog");
    assertEquals("positive", document.get(sentiment).get(0).value().polarity());
  }

  @Test
  void testAnnotatorAdaptersRejectNullDelegates() {
    assertThrows(IllegalArgumentException.class, () -> new SentenceDetectorAnnotator(null));
    assertThrows(IllegalArgumentException.class, () -> new TokenizerAnnotator(null));
    assertThrows(IllegalArgumentException.class, () -> new POSTaggerAnnotator(null));
    assertThrows(IllegalArgumentException.class, () -> new NameFinderAnnotator(null));
  }
}
