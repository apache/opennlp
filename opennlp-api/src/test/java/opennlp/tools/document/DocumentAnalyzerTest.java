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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link DocumentAnalyzer} pipeline over the adapter annotators, using the
 * deterministic components from {@link TestComponents} and a fixed-vocabulary tagger.
 * The point under test is the pipeline mechanics and span arithmetic, not model quality.
 */
public class DocumentAnalyzerTest {

  /** Tags the known verbs of the test texts {@code VBZ} and everything else {@code X}. */
  private static final POSTagger TAGGER = new POSTagger() {

    private final Set<String> verbs = Set.of("barks.", "eats.");

    @Override
    public String[] tag(String[] sentence) {
      final String[] tags = new String[sentence.length];
      for (int i = 0; i < sentence.length; i++) {
        tags[i] = verbs.contains(sentence[i]) ? "VBZ" : "X";
      }
      return tags;
    }

    @Override
    public String[] tag(String[] sentence, Object[] additionalContext) {
      return tag(sentence);
    }

    @Override
    public Sequence[] topKSequences(String[] sentence) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Sequence[] topKSequences(String[] sentence, Object[] additionalContext) {
      throw new UnsupportedOperationException();
    }
  };

  @Test
  void testPipelineProducesAlignedLayersInOriginalCoordinates() {
    final Document document = DocumentAnalyzer.builder()
        .add(new SentenceDetectorAnnotator(TestComponents.PERIOD_SPLITTER))
        .add(new TokenizerAnnotator(TestComponents.SPACE_TOKENIZER))
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

  /**
   * Verifies that a full pipeline over empty and whitespace-only input produces a
   * document on which every provided layer is present and empty, rather than failing:
   * zero sentences legitimately yield zero tokens, zero tags, and zero entities.
   */
  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void testEmptyAndBlankInputProduceEmptyLayers(String text) {
    final TokenNameFinder finder = new TokenNameFinder() {

      @Override
      public Span[] find(String[] tokens) {
        return new Span[0];
      }

      @Override
      public void clearAdaptiveData() {
      }
    };
    final DocumentAnalyzer analyzer = DocumentAnalyzer.builder()
        .add(new SentenceDetectorAnnotator(TestComponents.PERIOD_SPLITTER))
        .add(new TokenizerAnnotator(TestComponents.SPACE_TOKENIZER))
        .add(new POSTaggerAnnotator(TAGGER))
        .add(new NameFinderAnnotator(finder))
        .build();

    final Document document = analyzer.analyze(text);
    assertEquals(Set.of(Layers.SENTENCES, Layers.TOKENS, Layers.POS_TAGS, Layers.ENTITIES),
        document.layers());
    for (final LayerKey<?> layer : document.layers()) {
      assertTrue(document.get(layer).isEmpty());
    }
  }

  /**
   * Verifies that a present-but-empty sentence layer is honored as "no sentences": the
   * tokenizer adds a present-but-empty token layer instead of tokenizing the whole text.
   */
  @Test
  void testTokenizerHonorsPresentButEmptySentenceLayer() {
    final Document document = new TokenizerAnnotator(TestComponents.SPACE_TOKENIZER)
        .annotate(Document.of("the dog").with(Layers.SENTENCES, List.of()));
    assertTrue(document.layers().contains(Layers.TOKENS));
    assertTrue(document.get(Layers.TOKENS).isEmpty());
  }

  @Test
  void testTokenizerWorksWithoutSentences() {
    final Document document = DocumentAnalyzer.builder()
        .add(new TokenizerAnnotator(TestComponents.SPACE_TOKENIZER))
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
