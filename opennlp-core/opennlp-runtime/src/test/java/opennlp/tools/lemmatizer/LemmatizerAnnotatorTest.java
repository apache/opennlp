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

package opennlp.tools.lemmatizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;

public class LemmatizerAnnotatorTest {

  /** Lowercases verbs and keeps everything else, enough to observe the adapter. */
  private static final Lemmatizer FIXTURE = new Lemmatizer() {
    @Override
    public String[] lemmatize(String[] toks, String[] tags) {
      final String[] lemmas = new String[toks.length];
      for (int i = 0; i < toks.length; i++) {
        lemmas[i] = "VERB".equals(tags[i]) ? "run" : toks[i];
      }
      return lemmas;
    }

    @Override
    public List<List<String>> lemmatize(List<String> toks, List<String> tags) {
      throw new UnsupportedOperationException();
    }
  };

  @Test
  void testLemmasAlignWithTokens() {
    final Document document = Document.of("She ran home")
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 12), "She ran home")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "She"),
            new Annotation<>(new Span(4, 7), "ran"),
            new Annotation<>(new Span(8, 12), "home")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 3), "PRON"),
            new Annotation<>(new Span(4, 7), "VERB"),
            new Annotation<>(new Span(8, 12), "NOUN")));

    final Document lemmatized = new LemmatizerAnnotator(FIXTURE).annotate(document);

    final List<Annotation<String>> lemmas = lemmatized.get(LemmatizerAnnotator.LEMMAS);
    Assertions.assertEquals(3, lemmas.size());
    Assertions.assertEquals("run", lemmas.get(1).value());
    Assertions.assertEquals(new Span(4, 7), lemmas.get(1).span());
    Assertions.assertEquals("home", lemmas.get(2).value());
  }

  /**
   * Verifies that the lemmatizer is invoked once per sentence with exactly that
   * sentence's tokens and tags, so lemmatization decisions never see material from a
   * neighboring sentence, and that the lemma layer still aligns with the token layer.
   */
  @Test
  void testLemmatizesPerSentence() {
    final List<List<String>> calls = new ArrayList<>();
    final Lemmatizer recording = new Lemmatizer() {
      @Override
      public String[] lemmatize(String[] toks, String[] tags) {
        calls.add(List.of(toks));
        return toks.clone();
      }

      @Override
      public List<List<String>> lemmatize(List<String> toks, List<String> tags) {
        throw new UnsupportedOperationException();
      }
    };
    final Document document = Document.of("Ana runs. Bob sits.")
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 9), "Ana runs."),
            new Annotation<>(new Span(10, 19), "Bob sits.")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "Ana"),
            new Annotation<>(new Span(4, 9), "runs."),
            new Annotation<>(new Span(10, 13), "Bob"),
            new Annotation<>(new Span(14, 19), "sits.")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 3), "PROPN"),
            new Annotation<>(new Span(4, 9), "VERB"),
            new Annotation<>(new Span(10, 13), "PROPN"),
            new Annotation<>(new Span(14, 19), "VERB")));

    final Document lemmatized = new LemmatizerAnnotator(recording).annotate(document);

    Assertions.assertEquals(List.of(
        List.of("Ana", "runs."),
        List.of("Bob", "sits.")), calls);
    Assertions.assertEquals(4, lemmatized.get(LemmatizerAnnotator.LEMMAS).size());
    Assertions.assertEquals(new Span(10, 13),
        lemmatized.get(LemmatizerAnnotator.LEMMAS).get(2).span());
  }

  @Test
  void testInvalidArguments() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new LemmatizerAnnotator(null));
    final LemmatizerAnnotator annotator = new LemmatizerAnnotator(FIXTURE);
    Assertions.assertThrows(IllegalArgumentException.class, () -> annotator.annotate(null));
    final Document misaligned = Document.of("a b")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 3), "a b")))
        .with(Layers.TOKENS, List.of(new Annotation<>(new Span(0, 1), "a")))
        .with(Layers.POS_TAGS, List.of());
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(misaligned));
  }

  /**
   * Verifies that a document lacking a required layer is rejected with a message naming
   * the missing layer, for each of the three required layers in declaration order.
   */
  @Test
  void testAbsentRequiredLayerThrowsWithExactMessage() {
    final LemmatizerAnnotator annotator = new LemmatizerAnnotator(FIXTURE);
    final IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(Document.of("no layers")));
    Assertions.assertEquals("document lacks the required layer sentences<String>",
        e.getMessage());

    final Document sentencesOnly = Document.of("a")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 1), "a")));
    final IllegalArgumentException tokenless = Assertions.assertThrows(
        IllegalArgumentException.class, () -> annotator.annotate(sentencesOnly));
    Assertions.assertEquals("document lacks the required layer tokens<String>",
        tokenless.getMessage());

    final Document untagged = sentencesOnly
        .with(Layers.TOKENS, List.of(new Annotation<>(new Span(0, 1), "a")));
    final IllegalArgumentException tagless = Assertions.assertThrows(
        IllegalArgumentException.class, () -> annotator.annotate(untagged));
    Assertions.assertEquals("document lacks the required layer pos<String>",
        tagless.getMessage());
  }

  /**
   * Verifies that present-but-empty layers yield a present-but-empty lemma layer rather
   * than an exception.
   */
  @Test
  void testEmptyPresentLayersYieldEmptyLemmaLayer() {
    final Document document = Document.of("")
        .with(Layers.SENTENCES, List.of())
        .with(Layers.TOKENS, List.of())
        .with(Layers.POS_TAGS, List.of());
    final Document lemmatized = new LemmatizerAnnotator(FIXTURE).annotate(document);
    Assertions.assertTrue(lemmatized.layers().contains(LemmatizerAnnotator.LEMMAS));
    Assertions.assertTrue(lemmatized.get(LemmatizerAnnotator.LEMMAS).isEmpty());
  }

  /**
   * Verifies that a token lying outside every sentence is rejected loudly, matching the
   * walk contract of the other per-sentence adapters.
   */
  @Test
  void testTokenOutsideEverySentenceThrowsWithExactMessage() {
    final Document document = Document.of("Ana runs. Bob")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 9), "Ana runs.")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "Ana"),
            new Annotation<>(new Span(4, 9), "runs."),
            new Annotation<>(new Span(10, 13), "Bob")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 3), "PROPN"),
            new Annotation<>(new Span(4, 9), "VERB"),
            new Annotation<>(new Span(10, 13), "PROPN")));
    final IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> new LemmatizerAnnotator(FIXTURE).annotate(document));
    Assertions.assertEquals("token at [10..13) lies outside every sentence", e.getMessage());
  }

  /**
   * Verifies that a lemmatizer returning a wrong number of lemmas for a sentence is
   * rejected loudly instead of silently misaligning the lemma layer.
   */
  @Test
  void testWrongLemmaCountFailsLoud() {
    final Lemmatizer shortLemmatizer = new Lemmatizer() {
      @Override
      public String[] lemmatize(String[] toks, String[] tags) {
        return new String[] {"a"};
      }

      @Override
      public List<List<String>> lemmatize(List<String> toks, List<String> tags) {
        throw new UnsupportedOperationException();
      }
    };
    final Document document = Document.of("a b")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 3), "a b")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 1), "a"),
            new Annotation<>(new Span(2, 3), "b")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 1), "X"),
            new Annotation<>(new Span(2, 3), "X")));
    final IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> new LemmatizerAnnotator(shortLemmatizer).annotate(document));
    Assertions.assertEquals("lemmatizer returned 1 lemmas for 2 tokens", e.getMessage());
  }

  /**
   * Verifies that the adapter declares all three consumed layers as required, so a
   * pipeline without a sentence step fails at build time.
   */
  @Test
  void testRequiresSentencesTokensAndTags() {
    Assertions.assertEquals(Set.of(Layers.SENTENCES, Layers.TOKENS, Layers.POS_TAGS),
        new LemmatizerAnnotator(FIXTURE).requires());
  }
}
