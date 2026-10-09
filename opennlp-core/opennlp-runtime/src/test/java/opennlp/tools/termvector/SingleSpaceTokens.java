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
import opennlp.tools.document.DocumentTestStubs;
import opennlp.tools.util.Span;

/**
 * Deterministic single-space tokenization shared by the term vector tests: splits on
 * single space characters and keeps all other characters, including sentence-final
 * periods, attached to their token. Runs of spaces yield no empty tokens, so every
 * expected span follows directly from the input text.
 */
final class SingleSpaceTokens {

  private SingleSpaceTokens() {
  }

  /**
   * Builds a token layer from {@link DocumentTestStubs#SPACE_TOKENIZER}, each token valued
   * with its covered text.
   *
   * @param text The text to split.
   * @return One annotation per token, in text order.
   */
  static List<Annotation<String>> tokens(String text) {
    final List<Annotation<String>> tokens = new ArrayList<>();
    for (final Span span : DocumentTestStubs.SPACE_TOKENIZER.tokenizePos(text)) {
      tokens.add(new Annotation<>(span, text.substring(span.getStart(), span.getEnd())));
    }
    return tokens;
  }
}
