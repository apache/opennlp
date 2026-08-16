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

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.document.Annotation;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

/**
 * Deterministic single-space tokenization shared by the term vector tests: splits on
 * single space characters and keeps all other characters, including sentence-final
 * periods, attached to their token. Runs of spaces yield no empty tokens, so every
 * expected span follows directly from the input text.
 */
final class SingleSpaceTokens {

  /**
   * A {@link Tokenizer} view of {@link #spans(String)}. Only the span-producing method
   * is implemented because the annotator adapter calls no other method.
   */
  static final Tokenizer TOKENIZER = new Tokenizer() {

    @Override
    public String[] tokenize(String s) {
      throw new UnsupportedOperationException("the adapter only calls tokenizePos");
    }

    @Override
    public Span[] tokenizePos(String s) {
      return spans(s).toArray(new Span[0]);
    }
  };

  private SingleSpaceTokens() {
  }

  /**
   * Computes the token spans of a text split on single space characters.
   *
   * @param text The text to split.
   * @return One span per token, in text order.
   */
  static List<Span> spans(String text) {
    final List<Span> spans = new ArrayList<>();
    int start = -1;
    for (int i = 0; i <= text.length(); i++) {
      final boolean boundary = i == text.length() || text.charAt(i) == ' ';
      if (boundary && start >= 0) {
        spans.add(new Span(start, i));
        start = -1;
      } else if (!boundary && start < 0) {
        start = i;
      }
    }
    return spans;
  }

  /**
   * Builds a token layer from {@link #spans(String)}, each token valued with its covered
   * text.
   *
   * @param text The text to split.
   * @return One annotation per token, in text order.
   */
  static List<Annotation<String>> tokens(String text) {
    final List<Annotation<String>> tokens = new ArrayList<>();
    for (final Span span : spans(text)) {
      tokens.add(new Annotation<>(span, text.substring(span.getStart(), span.getEnd())));
    }
    return tokens;
  }
}
