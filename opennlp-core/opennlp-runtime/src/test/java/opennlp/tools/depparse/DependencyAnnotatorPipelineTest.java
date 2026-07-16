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

package opennlp.tools.depparse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnalyzer;
import opennlp.tools.document.Layers;
import opennlp.tools.document.POSTaggerAnnotator;
import opennlp.tools.document.SentenceDetectorAnnotator;
import opennlp.tools.document.TokenizerAnnotator;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.sentdetect.SentenceDetector;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Demonstrates {@link DependencyAnnotator} at the end of a complete {@link DocumentAnalyzer}
 * pipeline: raw text goes in, and the dependency layer comes out anchored on the original
 * text. The upstream steps are deliberately simple inline implementations of the task
 * interfaces, and the parser is a {@link DependencyParserME} trained here on a tiny corpus
 * it can memorize, so every expected head, relation, and span is exact and reproducible.
 *
 * <p>The central property under test is coordinate anchoring for multi-sentence input: the
 * annotator hands the whole token layer to the parser as one sequence, so the arcs it gets
 * back carry indices into the document-wide token layer, and each arc's annotation must sit
 * on its dependent token's span in document coordinates. For the second sentence this only
 * works when the token layer itself was anchored correctly, which the assertions verify by
 * reading the covered text of the arcs' spans back out of the original document.</p>
 */
public class DependencyAnnotatorPipelineTest {

  /**
   * The two-sentence input text; sentence one covers offsets 0..14 and sentence two covers
   * offsets 15..29 of the original document.
   */
  private static final String TEXT = "the dog barks. she eats fish.";

  /**
   * Maps every token of the test corpus to its part-of-speech tag, standing in for a
   * trained tagger.
   */
  private static final Map<String, String> LEXICON = Map.of(
      "the", "DT", "dog", "NN", "barks", "VBZ",
      "she", "PRP", "eats", "VBZ", "fish", "NN");

  /**
   * A sentence detector stub that closes a sentence after every period and skips the one
   * following blank, producing sentence spans in document coordinates.
   */
  private static final SentenceDetector PERIOD_SPLITTER = new SentenceDetector() {

    @Override
    public String[] sentDetect(CharSequence s) {
      throw new UnsupportedOperationException("the pipeline only calls sentPosDetect");
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
   * A tokenizer stub that treats blanks and periods as token boundaries, so word tokens
   * come out without trailing punctuation.
   */
  private static final Tokenizer WORD_TOKENIZER = new Tokenizer() {

    @Override
    public String[] tokenize(String s) {
      throw new UnsupportedOperationException("the pipeline only calls tokenizePos");
    }

    @Override
    public Span[] tokenizePos(String s) {
      final List<Span> spans = new ArrayList<>();
      int start = -1;
      for (int i = 0; i <= s.length(); i++) {
        final boolean boundary =
            i == s.length() || s.charAt(i) == ' ' || s.charAt(i) == '.';
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
   * A dictionary tagger over {@link #LEXICON} that fails loud on any token the test corpus
   * does not define, so a tokenization mistake cannot silently degrade the parse.
   */
  private static final POSTagger LEXICON_TAGGER = new POSTagger() {

    @Override
    public String[] tag(String[] sentence) {
      final String[] tags = new String[sentence.length];
      for (int i = 0; i < sentence.length; i++) {
        final String tag = LEXICON.get(sentence[i]);
        if (tag == null) {
          throw new IllegalArgumentException("token is not in the test lexicon: " + sentence[i]);
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
      throw new UnsupportedOperationException("the pipeline only calls tag");
    }

    @Override
    public Sequence[] topKSequences(String[] sentence, Object[] additionalContext) {
      throw new UnsupportedOperationException("the pipeline only calls tag");
    }
  };

  private static DependencyParserME parser;

  /**
   * Builds the training corpus: the token sequence of the two-sentence document with its
   * gold tree, plus a one-token sentence, each repeated often enough for the model to
   * memorize them. The document-wide sequence has a single root at {@code barks} with the
   * second predicate attached as {@code parataxis}, because a dependency graph always
   * forms one tree over the token sequence it is built for.
   *
   * @return The training samples. Never {@code null} or empty.
   */
  private static List<DependencySample> corpus() {
    final List<DependencySample> distinct = List.of(
        new DependencySample(
            new String[] {"the", "dog", "barks", "she", "eats", "fish"},
            new String[] {"DT", "NN", "VBZ", "PRP", "VBZ", "NN"},
            DependencyGraph.of(new int[] {1, 2, -1, 4, 2, 4},
                new String[] {"det", "nsubj", "root", "nsubj", "parataxis", "obj"})),
        new DependencySample(new String[] {"barks"}, new String[] {"VBZ"},
            DependencyGraph.of(new int[] {-1}, new String[] {"root"})));
    final List<DependencySample> corpus = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      corpus.addAll(distinct);
    }
    return corpus;
  }

  /**
   * Trains the shared parser once for all tests. The trainer is deterministic for a fixed
   * corpus and fixed parameters, so the assertions below hold on every run.
   *
   * @throws IOException Thrown if training fails, which fails the test class.
   */
  @BeforeAll
  static void trainParser() throws IOException {
    final TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, 0);
    parser = new DependencyParserME(DependencyParserME.train("eng",
        ObjectStreamUtils.createObjectStream(corpus()), parameters));
  }

  /**
   * Assembles the complete pipeline: sentence splitting, tokenization, tagging, and
   * dependency parsing with the trained model.
   *
   * @return A {@link DocumentAnalyzer} ready to analyze raw text. Never {@code null}.
   */
  private static DocumentAnalyzer pipeline() {
    return DocumentAnalyzer.builder()
        .add(new SentenceDetectorAnnotator(PERIOD_SPLITTER))
        .add(new TokenizerAnnotator(WORD_TOKENIZER))
        .add(new POSTaggerAnnotator(LEXICON_TAGGER))
        .add(new DependencyAnnotator(parser))
        .build();
  }

  @Test
  void testTwoSentenceTextYieldsOneExactArcPerTokenInDocumentCoordinates() {
    final Document document = pipeline().analyze(TEXT);

    // sanity of the upstream layers the dependency annotator consumed
    assertEquals(2, document.get(Layers.SENTENCES).size());
    assertEquals(6, document.get(Layers.TOKENS).size());

    final List<Annotation<DependencyArc>> arcs =
        document.get(DependencyAnnotator.DEPENDENCIES);
    assertEquals(6, arcs.size());

    // the memorized gold tree, with every span in document coordinates
    final int[] heads = {1, 2, DependencyArc.ROOT_HEAD, 4, 2, 4};
    final String[] relations = {"det", "nsubj", "root", "nsubj", "parataxis", "obj"};
    final Span[] spans = {new Span(0, 3), new Span(4, 7), new Span(8, 13),
        new Span(15, 18), new Span(19, 23), new Span(24, 28)};
    for (int i = 0; i < arcs.size(); i++) {
      final Annotation<DependencyArc> arc = arcs.get(i);
      assertEquals(spans[i], arc.span(), "span of arc " + i);
      assertEquals(heads[i], arc.value().head(), "head of arc " + i);
      assertEquals(i, arc.value().dependent(), "dependent of arc " + i);
      assertEquals(relations[i], arc.value().relation(), "relation of arc " + i);
    }
  }

  @Test
  void testSecondSentenceArcsResolveToTheOriginalText() {
    final Document document = pipeline().analyze(TEXT);
    final List<Annotation<DependencyArc>> arcs =
        document.get(DependencyAnnotator.DEPENDENCIES);

    // "she" is token 3 of the document-wide token layer, not token 0 of its sentence
    final Annotation<DependencyArc> she = arcs.get(3);
    assertEquals(new Span(15, 18), she.span());
    assertEquals("she", she.span().getCoveredText(document.text()).toString());
    assertEquals("nsubj", she.value().relation());

    // its head index is likewise document-wide: 4 points at "eats", never 1 at "dog"
    assertEquals(4, she.value().head());
    final Annotation<String> head = document.get(Layers.TOKENS).get(she.value().head());
    assertEquals("eats", head.value());
    assertEquals(new Span(19, 23), head.span());
    assertEquals("eats", head.span().getCoveredText(document.text()).toString());
  }

  @Test
  void testSingleTokenSentenceParsesToARootArc() {
    final Document document = pipeline().analyze("barks.");
    final List<Annotation<DependencyArc>> arcs =
        document.get(DependencyAnnotator.DEPENDENCIES);
    assertEquals(1, arcs.size());
    assertEquals(new Span(0, 5), arcs.get(0).span());
    assertEquals(DependencyArc.ROOT_HEAD, arcs.get(0).value().head());
    assertEquals(0, arcs.get(0).value().dependent());
    assertEquals("root", arcs.get(0).value().relation());
  }

  @Test
  void testTextWithZeroSentencesFailsBeforeTheDependencyAnnotatorRuns() {
    // empty text yields no sentences and no tokens, so the tagger already fails loud
    final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> pipeline().analyze(""));
    assertEquals("document lacks the required layer tokens<String>", e.getMessage());
  }
}
