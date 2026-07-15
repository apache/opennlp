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

import java.util.List;

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

  @Test
  void testInvalidArguments() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new LemmatizerAnnotator(null));
    final LemmatizerAnnotator annotator = new LemmatizerAnnotator(FIXTURE);
    Assertions.assertThrows(IllegalArgumentException.class, () -> annotator.annotate(null));
    final Document misaligned = Document.of("a b")
        .with(Layers.TOKENS, List.of(new Annotation<>(new Span(0, 1), "a")))
        .with(Layers.POS_TAGS, List.of());
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(misaligned));
  }
}
