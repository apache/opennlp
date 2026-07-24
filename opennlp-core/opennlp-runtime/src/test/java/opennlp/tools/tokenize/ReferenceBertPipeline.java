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

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * The reference BERT basic-tokenization stage feeding {@link WordpieceTokenizer}, kept
 * test-only as the frozen differential baseline for {@link WordpieceEncoderTest}: the
 * encoder's piece sequence must match this pipeline exactly.
 */
final class ReferenceBertPipeline {

  private static final int MAX_WORD_CHARACTERS = 100;

  private final WordpieceTokenizer wordpieceTokenizer;
  private final boolean lowerCase;

  ReferenceBertPipeline(Set<String> vocabulary, boolean lowerCase) {
    this.wordpieceTokenizer = new WordpieceTokenizer(vocabulary,
        WordpieceTokenizer.BERT_CLS_TOKEN, WordpieceTokenizer.BERT_SEP_TOKEN,
        WordpieceTokenizer.BERT_UNK_TOKEN, MAX_WORD_CHARACTERS);
    this.lowerCase = lowerCase;
  }

  String[] tokenize(String text) {
    return wordpieceTokenizer.tokenize(normalize(text));
  }

  private String normalize(String text) {
    String normalized = cleanText(text);
    normalized = isolateCjkCharacters(normalized);
    if (lowerCase) {
      // Locale.ROOT lower casing is the reference behavior of BERT's do_lower_case: the full
      // locale-independent Unicode case mappings, including one-to-many ones.
      normalized = stripAccents(normalized.toLowerCase(Locale.ROOT));
    }
    return BertNormalization.isolatePunctuation(normalized);
  }

  private static String cleanText(String text) {
    final StringBuilder cleaned = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> {
      if (codePoint == 0 || codePoint == 0xFFFD || BertNormalization.isControl(codePoint)) {
        return;
      }
      if (BertNormalization.isWhitespace(codePoint)) {
        cleaned.append(' ');
      } else {
        cleaned.appendCodePoint(codePoint);
      }
    });
    return cleaned.toString();
  }

  private static String isolateCjkCharacters(String text) {
    final StringBuilder spaced = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> {
      if (BertNormalization.isCjk(codePoint)) {
        spaced.append(' ').appendCodePoint(codePoint).append(' ');
      } else {
        spaced.appendCodePoint(codePoint);
      }
    });
    return spaced.toString();
  }

  private static String stripAccents(String text) {
    final String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
    final StringBuilder stripped = new StringBuilder(decomposed.length());
    decomposed.codePoints().forEach(codePoint -> {
      if (Character.getType(codePoint) != Character.NON_SPACING_MARK) {
        stripped.appendCodePoint(codePoint);
      }
    });
    return stripped.toString();
  }
}
