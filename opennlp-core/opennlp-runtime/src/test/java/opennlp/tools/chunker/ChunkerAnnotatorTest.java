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

package opennlp.tools.chunker;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Sequence;
import opennlp.tools.util.Span;

public class ChunkerAnnotatorTest {

  /**
   * A chunker that records every token and tag sequence it receives and answers with
   * one {@code NP} chunk per run of {@code N}-initial tags, so slicing and span mapping
   * are observable. Tests override {@link #chunkAsSpans(String[], String[])} where a
   * deviant answer is the fixture.
   */
  private static class RecordingChunker implements Chunker {

    private final List<List<String>> tokenCalls = new ArrayList<>();
    private final List<List<String>> tagCalls = new ArrayList<>();

    @Override
    public String[] chunk(String[] toks, String[] tags) {
      throw new UnsupportedOperationException("the adapter only calls chunkAsSpans");
    }

    @Override
    public Span[] chunkAsSpans(String[] toks, String[] tags) {
      tokenCalls.add(List.of(toks));
      tagCalls.add(List.of(tags));
      final List<Span> spans = new ArrayList<>();
      int start = -1;
      for (int i = 0; i <= tags.length; i++) {
        final boolean noun = i < tags.length && tags[i].startsWith("N");
        if (noun && start < 0) {
          start = i;
        } else if (!noun && start >= 0) {
          spans.add(new Span(start, i, "NP"));
          start = -1;
        }
      }
      return spans.toArray(new Span[0]);
    }

    @Override
    public Sequence[] topKSequences(String[] sentence, String[] tags) {
      throw new UnsupportedOperationException("the adapter only calls chunkAsSpans");
    }

    @Override
    public Sequence[] topKSequences(String[] sentence, String[] tags,
        double minSequenceScore) {
      throw new UnsupportedOperationException("the adapter only calls chunkAsSpans");
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

  private static List<Annotation<String>> values(List<Annotation<String>> tokens,
      String... tags) {
    final List<Annotation<String>> annotations = new ArrayList<>(tags.length);
    for (int i = 0; i < tags.length; i++) {
      annotations.add(new Annotation<>(tokens.get(i).span(), tags[i]));
    }
    return annotations;
  }

  /** Two sentences whose noun runs straddle neither sentence boundary. */
  private static Document twoSentences() {
    final String text = "Mary Jones leads Acme. She joined Acme Corp.";
    final List<Annotation<String>> toks = tokens(text,
        "Mary", "Jones", "leads", "Acme", ".", "She", "joined", "Acme", "Corp", ".");
    return Document.of(text)
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 22), "s"),
            new Annotation<>(new Span(23, 44), "s")))
        .with(Layers.TOKENS, toks)
        .with(Layers.POS_TAGS, values(toks,
            "NNP", "NNP", "VBZ", "NNP", ".", "PRP", "VBD", "NNP", "NNP", "."));
  }

  @Test
  void testChunksEachSentenceOntoTokenSpans() {
    final RecordingChunker chunker = new RecordingChunker();
    final Document document = new ChunkerAnnotator(chunker).annotate(twoSentences());

    Assertions.assertEquals(List.of(
        List.of("Mary", "Jones", "leads", "Acme", "."),
        List.of("She", "joined", "Acme", "Corp", ".")), chunker.tokenCalls);
    Assertions.assertEquals(List.of(
        List.of("NNP", "NNP", "VBZ", "NNP", "."),
        List.of("PRP", "VBD", "NNP", "NNP", ".")), chunker.tagCalls);
    final List<Annotation<String>> chunks = document.get(ChunkerAnnotator.CHUNKS);
    Assertions.assertEquals(List.of(
        new Annotation<>(new Span(0, 10), "NP"),
        new Annotation<>(new Span(17, 21), "NP"),
        new Annotation<>(new Span(34, 43), "NP")), chunks);
    Assertions.assertEquals("Acme Corp", document.text().subSequence(34, 43).toString());
  }

  @Test
  void testEmptyLayersYieldEmptyChunkLayer() {
    final Document document = new ChunkerAnnotator(new RecordingChunker()).annotate(
        Document.of("")
            .with(Layers.SENTENCES, List.of())
            .with(Layers.TOKENS, List.of())
            .with(Layers.POS_TAGS, List.of()));
    Assertions.assertTrue(document.layers().contains(ChunkerAnnotator.CHUNKS));
    Assertions.assertTrue(document.get(ChunkerAnnotator.CHUNKS).isEmpty());
  }

  @Test
  void testLayerContract() {
    final ChunkerAnnotator annotator = new ChunkerAnnotator(new RecordingChunker());
    Assertions.assertEquals(Set.of(Layers.SENTENCES, Layers.TOKENS, Layers.POS_TAGS),
        annotator.requires());
    Assertions.assertEquals(Set.of(ChunkerAnnotator.CHUNKS), annotator.provides());
    Assertions.assertEquals("opennlp:chunks", ChunkerAnnotator.CHUNKS.id());
    Assertions.assertEquals("ChunkerAnnotator", annotator.toString());
  }

  @Test
  void testRejectsNullChunkerAndMissingLayers() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new ChunkerAnnotator(null));
    final ChunkerAnnotator annotator = new ChunkerAnnotator(new RecordingChunker());
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(null));
    final Document untagged = Document.of("Mary.")
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 5), "s")))
        .with(Layers.TOKENS, tokens("Mary.", "Mary", "."));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(untagged));
  }

  @Test
  void testRejectsMisalignedTagLayer() {
    final String text = "Mary.";
    final List<Annotation<String>> toks = tokens(text, "Mary", ".");
    final Document document = Document.of(text)
        .with(Layers.SENTENCES, List.of(new Annotation<>(new Span(0, 5), "s")))
        .with(Layers.TOKENS, toks)
        .with(Layers.POS_TAGS, values(toks, "NNP"));
    final ChunkerAnnotator annotator = new ChunkerAnnotator(new RecordingChunker());
    final IllegalArgumentException rejection = Assertions.assertThrows(
        IllegalArgumentException.class, () -> annotator.annotate(document));
    Assertions.assertTrue(rejection.getMessage().contains("aligned"),
        rejection.getMessage());
  }

  @Test
  void testRejectsChunksOutsideSentenceOrWithoutType() {
    final ChunkerAnnotator outside = new ChunkerAnnotator(new RecordingChunker() {
      @Override
      public Span[] chunkAsSpans(String[] toks, String[] tags) {
        return new Span[] {new Span(0, toks.length + 1, "NP")};
      }
    });
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> outside.annotate(twoSentences()));
    final ChunkerAnnotator empty = new ChunkerAnnotator(new RecordingChunker() {
      @Override
      public Span[] chunkAsSpans(String[] toks, String[] tags) {
        return new Span[] {new Span(1, 1, "NP")};
      }
    });
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> empty.annotate(twoSentences()));
    final ChunkerAnnotator untyped = new ChunkerAnnotator(new RecordingChunker() {
      @Override
      public Span[] chunkAsSpans(String[] toks, String[] tags) {
        return new Span[] {new Span(0, 1)};
      }
    });
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> untyped.annotate(twoSentences()));
  }
}
