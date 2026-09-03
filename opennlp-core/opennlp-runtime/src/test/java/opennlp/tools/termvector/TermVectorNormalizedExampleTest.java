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

import org.junit.jupiter.api.Test;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnalyzer;
import opennlp.tools.tokenize.TokenizerAnnotator;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.Span;
import opennlp.tools.util.normalizer.CharSequenceNormalizer;
import opennlp.tools.util.normalizer.TextNormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors the normalized-term workflow shown in the term vector section of the manual:
 * a whitespace tokenizer feeding a {@link TermVectorAnnotator} built with a shipped,
 * plain {@link CharSequenceNormalizer} case folder. The folder defines term identity per
 * token, so case variants group under one term, while every occurrence span stays the
 * token's own span in the original text.
 */
public class TermVectorNormalizedExampleTest {

  /**
   * The documented example: {@code "Word word WORD"} yields one term, {@code "word"},
   * with three occurrence spans, each the token's exact original span.
   */
  @Test
  void testCaseFoldedTermsKeepOriginalSpans() {
    final CharSequenceNormalizer folder = TextNormalizer.builder().caseFold().build();
    final DocumentAnalyzer analyzer = DocumentAnalyzer.builder()
        .add(new TokenizerAnnotator(WhitespaceTokenizer.INSTANCE))
        .add(new TermVectorAnnotator(folder))
        .build();

    final Document document = analyzer.analyze("Word word WORD");

    final List<Annotation<TermVector>> vectors =
        document.get(TermVectorAnnotator.TERM_VECTORS);
    assertEquals(1, vectors.size());
    assertEquals(new TermVector("word", 3,
        List.of(new Span(0, 4), new Span(5, 9), new Span(10, 14))), vectors.get(0).value());

    // The spans point at the original surface forms, not the folded term.
    final List<String> surfaceForms = vectors.get(0).value().spans().stream()
        .map(s -> s.getCoveredText(document.text()).toString()).toList();
    assertEquals(List.of("Word", "word", "WORD"), surfaceForms);
  }
}
