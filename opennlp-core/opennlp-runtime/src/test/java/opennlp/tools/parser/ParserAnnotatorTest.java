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

package opennlp.tools.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.parser.ParserAnnotator.Phrase;
import opennlp.tools.util.Span;

public class ParserAnnotatorTest {

  /**
   * A parser that builds one fixed bracketing over any sentence of six tokens,
   * {@code (S (NP (NP 0 1) (PP 2 (NP 3))) (VP 4) 5)}, with explicit heads, so the
   * span and head mapping is observable without a model.
   */
  private static class FixedParser implements Parser {

    @Override
    public Parse[] parse(Parse tokens, int numParses) {
      return new Parse[] {parse(tokens)};
    }

    @Override
    public Parse parse(Parse tokens) {
      final Parse[] toks = tokens.getChildren();
      if (toks.length != 6) {
        return tokens;
      }
      final String[] tags = {"DT", "NN", "IN", "NNP", "VBD", "."};
      final Parse[] pos = new Parse[toks.length];
      for (int i = 0; i < toks.length; i++) {
        pos[i] = node(tokens, tags[i], toks[i], toks[i], toks[i]);
        tokens.insert(pos[i]);
      }
      final Parse innerNp = node(tokens, "NP", pos[0], pos[1], toks[1]);
      tokens.insert(innerNp);
      final Parse maryNp = node(tokens, "NP", pos[3], pos[3], toks[3]);
      tokens.insert(maryNp);
      final Parse pp = node(tokens, "PP", pos[2], maryNp, toks[2]);
      tokens.insert(pp);
      final Parse outerNp = node(tokens, "NP", innerNp, pp, toks[1]);
      tokens.insert(outerNp);
      final Parse vp = node(tokens, "VP", pos[4], pos[4], toks[4]);
      tokens.insert(vp);
      final Parse s = node(tokens, "S", outerNp, pos[5], toks[4]);
      tokens.insert(s);
      return tokens;
    }

    private static Parse node(Parse root, String type, Parse from, Parse to, Parse head) {
      return new Parse(root.getText(),
          new Span(from.getSpan().getStart(), to.getSpan().getEnd()), type, 1.0, head);
    }
  }

  private static List<Annotation<String>> tokens(String text, String... forms) {
    final List<Annotation<String>> annotations = new ArrayList<>(forms.length);
    int cursor = 0;
    for (final String form : forms) {
      final int start = text.indexOf(form, cursor);
      annotations.add(new Annotation<>(new Span(start, start + form.length()), form));
      cursor = start + form.length();
    }
    return annotations;
  }

  /** One six-token sentence whose text carries a double space the parse text lacks. */
  private static Document sentence() {
    final String text = "The dog  of Mary ran.";
    return Document.of(text)
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 21), "s")))
        .with(Layers.TOKENS, tokens(text, "The", "dog", "of", "Mary", "ran", "."));
  }

  @Test
  void testEmitsPhrasesInPreOrderOnTokenSpansWithHeads() {
    final Document document = new ParserAnnotator(new FixedParser()).annotate(sentence());

    final List<Annotation<Phrase>> phrases = document.get(ParserAnnotator.PHRASES);
    Assertions.assertEquals(List.of("S", "NP", "NP", "PP", "NP", "VP"),
        phrases.stream().map(a -> a.value().label()).toList());
    Assertions.assertEquals(List.of(
            new Span(0, 21), new Span(0, 16), new Span(0, 7), new Span(9, 16),
            new Span(12, 16), new Span(17, 20)),
        phrases.stream().map(Annotation::span).toList());
    Assertions.assertEquals("The dog  of Mary", document.text().subSequence(0, 16).toString());
    final Span dog = new Span(4, 7);
    final Span ran = new Span(17, 20);
    Assertions.assertEquals(List.of(ran, dog, dog, new Span(9, 11), new Span(12, 16), ran),
        phrases.stream().map(a -> a.value().head()).toList());
  }

  @Test
  void testEmptyLayersYieldEmptyPhraseLayer() {
    final Document document = new ParserAnnotator(new FixedParser()).annotate(
        Document.of("").with(Layers.SENTENCES, List.of()).with(Layers.TOKENS, List.of()));
    Assertions.assertTrue(document.layers().contains(ParserAnnotator.PHRASES));
    Assertions.assertTrue(document.get(ParserAnnotator.PHRASES).isEmpty());
  }

  @Test
  void testLayerContract() {
    final ParserAnnotator annotator = new ParserAnnotator(new FixedParser());
    Assertions.assertEquals(Set.of(Layers.SENTENCES, Layers.TOKENS), annotator.requires());
    Assertions.assertEquals(Set.of(ParserAnnotator.PHRASES), annotator.provides());
    Assertions.assertEquals("opennlp:phrases", ParserAnnotator.PHRASES.id());
    Assertions.assertEquals("ParserAnnotator", annotator.toString());
  }

  @Test
  void testRejectsNullParserMissingLayersAndNullParse() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new ParserAnnotator(null));
    final ParserAnnotator annotator = new ParserAnnotator(new FixedParser());
    Assertions.assertThrows(IllegalArgumentException.class, () -> annotator.annotate(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(Document.of("x")));
    final ParserAnnotator silent = new ParserAnnotator(new FixedParser() {
      @Override
      public Parse parse(Parse tokens) {
        return null;
      }
    });
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> silent.annotate(sentence()));
  }

  @Test
  void testPhraseRejectsBlankLabelAndNullHead() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new Phrase(" ", new Span(0, 1)));
    Assertions.assertThrows(IllegalArgumentException.class, () -> new Phrase("NP", null));
  }
}
