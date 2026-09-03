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

package opennlp.tools.termvector;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnalyzer;
import opennlp.tools.document.Layers;
import opennlp.tools.tokenize.TokenizerAnnotator;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Wires the {@link TermVectorAnnotator} into a {@link DocumentAnalyzer} behind a
 * {@link TokenizerAnnotator}: the token layer goes in, the term vector layer comes out,
 * and nothing else about the document changes. The tokenizer is the shared deterministic
 * single-space fixture, so every expected span follows directly from the input text.
 */
public class TermVectorPipelineTest {

  /**
   * Runs tokenizer plus term vector roll-up over a text with repeated tokens and reads
   * the aggregated layer back, span by span, in original text coordinates.
   */
  @Test
  void testTokenizerAndTermVectorPipeline() {
    final DocumentAnalyzer analyzer = DocumentAnalyzer.builder()
        .add(new TokenizerAnnotator(SingleSpaceTokens.TOKENIZER))
        .add(new TermVectorAnnotator())
        .build();

    final Document document = analyzer.analyze("The dog barks. The dog naps.");

    // The document carries exactly the two layers the pipeline provides.
    assertEquals(Set.of(Layers.TOKENS, TermVectorAnnotator.TERM_VECTORS), document.layers());

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(4, vectors.size());
    assertEquals(new TermVector("The", 2, List.of(new Span(0, 3), new Span(15, 18))),
        vectors.get(0).value());
    assertEquals(new TermVector("dog", 2, List.of(new Span(4, 7), new Span(19, 22))),
        vectors.get(1).value());
    assertEquals(new TermVector("barks.", 1, List.of(new Span(8, 14))),
        vectors.get(2).value());
    assertEquals(new TermVector("naps.", 1, List.of(new Span(23, 28))),
        vectors.get(3).value());

    // Every occurrence span indexes into the original text and covers its own term.
    for (final Annotation<TermVector> vector : vectors) {
      for (final Span span : vector.value().spans()) {
        assertEquals(vector.value().term(), span.getCoveredText(document.text()).toString());
      }
    }
  }

  /**
   * The same pipeline in scoring-only mode: counts survive, offsets are never stored.
   */
  @Test
  void testScoringOnlyPipeline() {
    final DocumentAnalyzer analyzer = DocumentAnalyzer.builder()
        .add(new TokenizerAnnotator(SingleSpaceTokens.TOKENIZER))
        .add(new TermVectorAnnotator(TermVectorAnnotator.Mode.SCORING_ONLY))
        .build();

    final Document document = analyzer.analyze("The dog barks. The dog naps.");

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(4, vectors.size());
    assertEquals(TermVector.count("The", 2), vectors.get(0).value());
    assertEquals(TermVector.count("dog", 2), vectors.get(1).value());
  }

  /**
   * The analyzer validates the pipeline at build time: the term vector annotator
   * requires the token layer, so a pipeline without a tokenizer fails when it is
   * assembled.
   */
  @Test
  void testPipelineWithoutTokenizerIsRejectedAtBuildTime() {
    final DocumentAnalyzer.Builder builder = DocumentAnalyzer.builder()
        .add(new TermVectorAnnotator());
    assertThrows(IllegalArgumentException.class, builder::build);
  }
}
