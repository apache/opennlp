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

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnnotator;
import opennlp.tools.document.DocumentAnnotators;
import opennlp.tools.document.LayerKey;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Adapts a constituency {@link Parser} to the document pipeline: reads
 * {@link Layers#SENTENCES} and {@link Layers#TOKENS} and provides {@link #PHRASES}, one
 * annotation per phrase node of each sentence's parse, carrying the phrase label and the
 * span of its head token.
 *
 * <p>Each sentence is parsed from its tokens as one sequence. Every node above the
 * part-of-speech level except the root becomes an annotation on the span from its first
 * to its last token, in pre-order, so an enclosing phrase precedes the phrases it
 * contains and phrases nest by span containment. Part-of-speech nodes are left to the
 * {@link Layers#POS_TAGS} layer and token nodes to {@link Layers#TOKENS}. The head token
 * is the one the parser's head rules select, so a consumer can read the head of a noun
 * phrase without its own rules.</p>
 *
 * <p>The adapter holds no per-call state; it is as thread-safe as the parser it
 * wraps.</p>
 *
 * @since 3.0.0
 */
public final class ParserAnnotator implements DocumentAnnotator {

  /**
   * One phrase of a constituency parse: its label, such as {@code NP} or {@code VP},
   * and the span of the token that heads it. The phrase's own span is the annotation's
   * span.
   *
   * @param label The phrase label. Must not be {@code null} or blank.
   * @param head The span of the head token in the document text. Must not be
   *             {@code null}.
   *
   * @since 3.0.0
   */
  public record Phrase(String label, Span head) {

    /**
     * Validates the phrase.
     *
     * @throws IllegalArgumentException Thrown if {@code label} is {@code null} or
     *         blank, or {@code head} is {@code null}.
     */
    public Phrase {
      if (label == null || StringUtil.isBlank(label)) {
        throw new IllegalArgumentException("label must not be null or blank");
      }
      if (head == null) {
        throw new IllegalArgumentException("head must not be null");
      }
    }
  }

  /**
   * Parse phrases; each annotation covers one phrase and carries its {@link Phrase},
   * in pre-order of the parse tree.
   */
  public static final LayerKey<Phrase> PHRASES = Layers.key("phrases", Phrase.class);

  private final Parser parser;

  /**
   * Initializes the adapter.
   *
   * @param parser The parser to delegate to. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code parser} is {@code null}.
   */
  public ParserAnnotator(Parser parser) {
    if (parser == null) {
      throw new IllegalArgumentException("parser must not be null");
    }
    this.parser = parser;
  }

  /**
   * Parses the document sentence by sentence and adds the {@link #PHRASES} layer.
   *
   * <p>The required layers must be present, but they may be empty: a document without
   * sentences or tokens yields a present-but-empty phrase layer.</p>
   *
   * @param document The document to annotate. Must not be {@code null} and must carry
   *                 the {@link Layers#SENTENCES} and {@link Layers#TOKENS} layers, with
   *                 every token lying inside a sentence.
   * @return A new {@link Document} with the {@link #PHRASES} layer added. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null}, a
   *         required layer is absent, a token lies outside every sentence, or the
   *         parser returns a node outside the sentence's tokens.
   */
  @Override
  public Document annotate(Document document) {
    DocumentAnnotators.requireLayers(document, Layers.SENTENCES, Layers.TOKENS);
    final List<Annotation<String>> sentences = document.get(Layers.SENTENCES);
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final List<Annotation<Phrase>> phrases = new ArrayList<>();
    DocumentAnnotators.forEachSentence(sentences, tokens, (first, words) -> {
      final Parse root = parser.parse(Parse.createFromTokens(words));
      if (root == null) {
        throw new IllegalArgumentException("parser returned no parse");
      }
      // The parse text is the tokens joined by single spaces, so a token's start in
      // that text identifies its index.
      final int[] starts = new int[words.length];
      for (int i = 1; i < words.length; i++) {
        starts[i] = starts[i - 1] + words[i - 1].length() + 1;
      }
      final int length = starts[words.length - 1] + words[words.length - 1].length();
      for (final Parse child : root.getChildren()) {
        collect(child, first, starts, length, tokens, phrases);
      }
    });
    return document.with(PHRASES, phrases);
  }

  /** Emits a node and, in pre-order, every phrase node below it. */
  private static void collect(Parse node, int first, int[] starts, int length,
      List<Annotation<String>> tokens, List<Annotation<Phrase>> phrases) {
    if (node.isPosTag() || Parser.TOK_NODE.equals(node.getType())) {
      return;
    }
    final int from = tokenIndex(starts, length, node.getSpan().getStart(), node);
    final int to = tokenIndex(starts, length, node.getSpan().getEnd(), node);
    final int head = node.getHeadIndex();
    if (head < 0 || head >= starts.length) {
      throw new IllegalArgumentException("parser returned node " + node.getType()
          + " with head " + head + " outside the sentence's " + starts.length + " tokens");
    }
    phrases.add(new Annotation<>(new Span(tokens.get(first + from).span().getStart(),
        tokens.get(first + to).span().getEnd()),
        new Phrase(node.getType(), tokens.get(first + head).span())));
    for (final Parse child : node.getChildren()) {
      collect(child, first, starts, length, tokens, phrases);
    }
  }

  /**
   * Maps an offset in the parse text, the tokens joined by single spaces, to the index
   * of the token it lies in or, for a span end, ends.
   */
  private static int tokenIndex(int[] starts, int length, int offset, Parse node) {
    if (offset < 0 || offset > length) {
      throw new IllegalArgumentException("parser returned node " + node.getType()
          + " at " + node.getSpan() + " outside the sentence's " + starts.length
          + " tokens");
    }
    int low = 0;
    int high = starts.length - 1;
    while (low < high) {
      final int mid = (low + high + 1) >>> 1;
      if (starts[mid] <= offset) {
        low = mid;
      } else {
        high = mid - 1;
      }
    }
    return low;
  }

  /** {@inheritDoc} */
  @Override
  public Set<LayerKey<?>> requires() {
    return Set.of(Layers.SENTENCES, Layers.TOKENS);
  }

  /** {@inheritDoc} */
  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(PHRASES);
  }

  /**
   * {@return the adapter's simple class name, which names it in pipeline validation
   * messages}
   */
  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
