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

package opennlp.tools.tokenize;

import java.util.ArrayList;
import java.util.List;

import opennlp.tools.util.Span;
import opennlp.tools.util.normalizer.UnicodeWhitespace;

/**
 * A basic {@link Tokenizer} implementation which performs tokenization
 * using white spaces.
 * <p>
 * Whitespace is the Unicode {@code White_Space} set
 * ({@link UnicodeWhitespace#isWhitespace(int)}). Since 3.0 the next line control
 * {@code U+0085} separates tokens and the {@code U+001C}..{@code U+001F} information
 * separators no longer do, matching the standard instead of the JVM predicates. Token
 * boundaries produced for text containing those code points differ from earlier releases,
 * which can also shift the candidate spans {@code TokenizerME} scores for such text.
 * <p>
 * To obtain an instance of this tokenizer use the static final
 * {@link #INSTANCE} field.
 */
public class WhitespaceTokenizer extends AbstractTokenizer {

  /**
   * Use this static reference to retrieve an instance of the
   * {@link WhitespaceTokenizer}.
   */
  public static final WhitespaceTokenizer INSTANCE = new WhitespaceTokenizer();

  /*
   * Use the {@link WhitespaceTokenizer#INSTANCE} field to retrieve an instance.
   */
  private WhitespaceTokenizer() {
  }

  @Override
  public Span[] tokenizePos(String d) {
    int tokStart = -1;
    List<Span> tokens = new ArrayList<>();
    boolean inTok = false;

    // gather potential tokens
    int end = d.length();
    for (int i = 0; i < end; i++) {
      if (UnicodeWhitespace.isWhitespace(d.charAt(i))) {
        if (inTok) {
          tokens.add(new Span(tokStart, i));
          inTok = false;
          tokStart = -1;
        }
        if (keepNewLines && isLineSeparator(d.charAt(i))) {
          tokStart = i;
          tokens.add(new Span(tokStart, tokStart + 1));
          tokStart = -1;
        }
      } else {
        if (!inTok) {
          tokStart = i;
          inTok = true;
        }
      }
    }

    if (inTok) {
      tokens.add(new Span(tokStart, end));
    }

    return tokens.toArray(new Span[0]);
  }

  private boolean isLineSeparator(char character) {
    return character == Character.LINE_SEPARATOR || character == Character.LETTER_NUMBER;
  }

}
