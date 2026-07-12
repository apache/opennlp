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
package opennlp.embeddings;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import opennlp.tools.tokenize.WordpieceTokenizer;

/**
 * The full BERT tokenization pipeline used for embedding-table lookup: basic tokenization
 * (control removal, whitespace normalization, CJK isolation, optional lower casing with
 * accent stripping, punctuation isolation) followed by {@link WordpieceTokenizer} with the
 * BERT special tokens and the reference 100-character word limit.
 *
 * <p>This module-internal pipeline exists so the embedding hot path depends only on the
 * stable wordpiece stage; it produces pieces without offset bookkeeping, which the lookup
 * path does not need.</p>
 */
final class WordpiecePipeline {

  private static final int MAX_WORD_CHARACTERS = 100;

  private final WordpieceTokenizer wordpieceTokenizer;
  private final boolean lowerCase;

  WordpiecePipeline(Set<String> vocabulary, boolean lowerCase) {
    Objects.requireNonNull(vocabulary, "vocabulary must not be null");
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
      normalized = stripAccents(normalized.toLowerCase(Locale.ROOT));
    }
    return isolatePunctuation(normalized);
  }

  private static String cleanText(String text) {
    final StringBuilder cleaned = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> {
      if (codePoint == 0 || codePoint == 0xFFFD || isControl(codePoint)) {
        return;
      }
      if (isWhitespace(codePoint)) {
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
      if (isCjk(codePoint)) {
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

  private static String isolatePunctuation(String text) {
    final StringBuilder spaced = new StringBuilder(text.length());
    text.codePoints().forEach(codePoint -> {
      if (isPunctuation(codePoint)) {
        spaced.append(' ').appendCodePoint(codePoint).append(' ');
      } else {
        spaced.appendCodePoint(codePoint);
      }
    });
    return spaced.toString();
  }

  private static boolean isControl(int codePoint) {
    if (codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
      return false;
    }
    return switch (Character.getType(codePoint)) {
      case Character.CONTROL, Character.FORMAT, Character.SURROGATE,
           Character.PRIVATE_USE, Character.UNASSIGNED -> true;
      default -> false;
    };
  }

  private static boolean isWhitespace(int codePoint) {
    if (codePoint == ' ' || codePoint == '\t' || codePoint == '\n' || codePoint == '\r') {
      return true;
    }
    return Character.getType(codePoint) == Character.SPACE_SEPARATOR;
  }

  private static boolean isPunctuation(int codePoint) {
    if ((codePoint >= 33 && codePoint <= 47) || (codePoint >= 58 && codePoint <= 64)
        || (codePoint >= 91 && codePoint <= 96) || (codePoint >= 123 && codePoint <= 126)) {
      return true;
    }
    return switch (Character.getType(codePoint)) {
      case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
           Character.START_PUNCTUATION, Character.END_PUNCTUATION,
           Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
           Character.OTHER_PUNCTUATION -> true;
      default -> false;
    };
  }

  private static boolean isCjk(int codePoint) {
    return (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
        || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
        || (codePoint >= 0x20000 && codePoint <= 0x2A6DF)
        || (codePoint >= 0x2A700 && codePoint <= 0x2B73F)
        || (codePoint >= 0x2B740 && codePoint <= 0x2B81F)
        || (codePoint >= 0x2B820 && codePoint <= 0x2CEAF)
        || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
        || (codePoint >= 0x2F800 && codePoint <= 0x2FA1F);
  }
}
