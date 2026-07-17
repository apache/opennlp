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
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import opennlp.tools.postag.POSTagger;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that {@link POSTaggerAnnotator} tags one sentence per {@link POSTagger#tag(String[])}
 * call, keeps the tag layer aligned with the token layer, distinguishes a present-but-empty
 * required layer from an absent one, and rejects tokens outside every sentence.
 */
public class POSTaggerAnnotatorTest {

  /**
   * A tagger that records the exact token sequence of every call and answers with one
   * {@code "X"} tag per token, so the per-call slicing is observable.
   */
  private static final class RecordingTagger implements POSTagger {

    private final List<List<String>> calls = new ArrayList<>();

    @Override
    public String[] tag(String[] sentence) {
      calls.add(List.of(sentence));
      final String[] tags = new String[sentence.length];
      Arrays.fill(tags, "X");
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
  }

  /**
   * @return A two-sentence document with sentence and token layers over
   *         {@code "The dog barks. It naps."}. Never {@code null}.
   */
  private static Document twoSentenceDocument() {
    return Document.of("The dog barks. It naps.")
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 14), "The dog barks."),
            new Annotation<>(new Span(15, 23), "It naps.")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "The"),
            new Annotation<>(new Span(4, 7), "dog"),
            new Annotation<>(new Span(8, 14), "barks."),
            new Annotation<>(new Span(15, 17), "It"),
            new Annotation<>(new Span(18, 23), "naps.")));
  }

  /**
   * Verifies that the tagger is invoked once per sentence with exactly that sentence's
   * tokens, and that the resulting tag layer stays aligned with the token layer by
   * position, each tag on its token's span.
   */
  @Test
  void testTagsEachSentenceSeparately() {
    final RecordingTagger tagger = new RecordingTagger();
    final Document annotated = new POSTaggerAnnotator(tagger).annotate(twoSentenceDocument());

    assertEquals(List.of(
        List.of("The", "dog", "barks."),
        List.of("It", "naps.")), tagger.calls);

    final List<Annotation<String>> tokens = annotated.get(Layers.TOKENS);
    final List<Annotation<String>> tags = annotated.get(Layers.POS_TAGS);
    assertEquals(tokens.size(), tags.size());
    for (int i = 0; i < tags.size(); i++) {
      assertEquals(tokens.get(i).span(), tags.get(i).span());
      assertEquals("X", tags.get(i).value());
    }
  }

  /**
   * Verifies that the annotator declares both the sentence layer and the token layer as
   * required, so a pipeline without a sentence step fails at build time.
   */
  @Test
  void testRequiresSentencesAndTokens() {
    assertEquals(Set.of(Layers.SENTENCES, Layers.TOKENS),
        new POSTaggerAnnotator(new RecordingTagger()).requires());
  }

  /**
   * Verifies that present-but-empty sentence and token layers yield a present-but-empty
   * tag layer without invoking the tagger, rather than an exception.
   */
  @Test
  void testEmptyPresentLayersYieldEmptyTagLayer() {
    final RecordingTagger tagger = new RecordingTagger();
    final Document document = Document.of("")
        .with(Layers.SENTENCES, List.of())
        .with(Layers.TOKENS, List.of());

    final Document annotated = new POSTaggerAnnotator(tagger).annotate(document);

    assertTrue(annotated.layers().contains(Layers.POS_TAGS));
    assertTrue(annotated.get(Layers.POS_TAGS).isEmpty());
    assertTrue(tagger.calls.isEmpty());
  }

  /**
   * Verifies that a document without a sentence layer is rejected with a message naming
   * the missing layer.
   */
  @Test
  void testAbsentSentenceLayerThrowsWithExactMessage() {
    final Document document = Document.of("The dog")
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "The"),
            new Annotation<>(new Span(4, 7), "dog")));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new POSTaggerAnnotator(new RecordingTagger()).annotate(document));
    assertEquals("document lacks the required layer sentences<String>", e.getMessage());
  }

  /**
   * Verifies that a document without a token layer is rejected with a message naming the
   * missing layer.
   */
  @Test
  void testAbsentTokenLayerThrowsWithExactMessage() {
    final Document document = Document.of("The dog")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 7), "The dog")));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new POSTaggerAnnotator(new RecordingTagger()).annotate(document));
    assertEquals("document lacks the required layer tokens<String>", e.getMessage());
  }

  /**
   * Verifies that a token whose span no sentence encloses is rejected with a message
   * naming the token's span.
   */
  @Test
  void testTokenOutsideEverySentenceThrowsWithExactMessage() {
    final Document document = Document.of("The dog")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 3), "The")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "The"),
            new Annotation<>(new Span(4, 7), "dog")));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new POSTaggerAnnotator(new RecordingTagger()).annotate(document));
    assertEquals("token at [4..7) lies outside every sentence", e.getMessage());
  }

  /**
   * Verifies that a sentence containing no tokens contributes nothing: the tagger is
   * never called with an empty sequence and the tag layer still matches the token layer.
   */
  @Test
  void testSentenceWithoutTokensContributesNothing() {
    final RecordingTagger tagger = new RecordingTagger();
    final Document document = Document.of("The ???")
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 3), "The"),
            new Annotation<>(new Span(4, 7), "???")))
        .with(Layers.TOKENS, List.of(new Annotation<>(new Span(0, 3), "The")));

    final Document annotated = new POSTaggerAnnotator(tagger).annotate(document);

    assertEquals(List.of(List.of("The")), tagger.calls);
    assertEquals(1, annotated.get(Layers.POS_TAGS).size());
  }

  /**
   * Verifies that a tagger returning a wrong number of tags for a sentence is rejected
   * loudly instead of silently misaligning the tag layer with the token layer.
   */
  @Test
  void testWrongTagCountFailsLoud() {
    final POSTagger shortTagger = new POSTagger() {

      @Override
      public String[] tag(String[] sentence) {
        return new String[] {"X"};
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
    final Document document = Document.of("The dog")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 7), "The dog")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "The"),
            new Annotation<>(new Span(4, 7), "dog")));
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> new POSTaggerAnnotator(shortTagger).annotate(document));
    assertEquals("tagger returned 1 tags for 2 tokens", e.getMessage());
  }
}
