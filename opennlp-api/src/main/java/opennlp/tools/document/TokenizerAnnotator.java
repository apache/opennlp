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
import java.util.List;
import java.util.Set;

import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

/**
 * Adapts a {@link Tokenizer} to the document pipeline: provides {@link Layers#TOKENS}.
 *
 * <p>When {@link Layers#SENTENCES} is present, each sentence is tokenized separately and
 * the token spans are shifted back to document coordinates; a present-but-empty sentence
 * layer therefore yields a present-but-empty token layer. Only when the sentence layer
 * is absent is the whole text tokenized at once. Either way, every token span refers to
 * the original document text.</p>
 *
 * @since 3.0.0
 */
public class TokenizerAnnotator implements DocumentAnnotator {

  private final Tokenizer tokenizer;

  /**
   * Initializes the adapter.
   *
   * @param tokenizer The tokenizer to delegate to. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code tokenizer} is {@code null}.
   */
  public TokenizerAnnotator(Tokenizer tokenizer) {
    if (tokenizer == null) {
      throw new IllegalArgumentException("tokenizer must not be null");
    }
    this.tokenizer = tokenizer;
  }

  @Override
  public Document annotate(Document document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    final String text = document.text().toString();
    final List<Annotation<String>> tokens = new ArrayList<>();
    if (!document.layers().contains(Layers.SENTENCES)) {
      addTokens(tokens, text, 0);
    } else {
      for (final Annotation<String> sentence : document.get(Layers.SENTENCES)) {
        final Span span = sentence.span();
        addTokens(tokens, text.substring(span.getStart(), span.getEnd()), span.getStart());
      }
    }
    return document.with(Layers.TOKENS, tokens);
  }

  private void addTokens(List<Annotation<String>> tokens, String text, int offset) {
    for (final Span span : tokenizer.tokenizePos(text)) {
      final Span shifted = new Span(span.getStart() + offset, span.getEnd() + offset);
      tokens.add(new Annotation<>(shifted, span.getCoveredText(text).toString()));
    }
  }

  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(Layers.TOKENS);
  }
}
