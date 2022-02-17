/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.tokenize;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import opennlp.tools.util.Span;

/**
 * A WordPiece tokenizer.
 *
 * Adapted from https://github.com/robrua/easy-bert under the MIT license.
 *
 * For reference see:
 *  - https://www.tensorflow.org/text/guide/subwords_tokenizer#applying_wordpiece
 *  - https://cran.r-project.org/web/packages/wordpiece/vignettes/basic_usage.html
 */
public class WordpieceTokenizer implements Tokenizer {

  private static final String CLASSIFICATION_TOKEN = "[CLS]";
  private static final String SEPARATOR_TOKEN = "[SEP]";
  private static final String UNKNOWN_TOKEN = "[UNK]";

  private Set<String> vocabulary;
  private int maxTokenLength = 50;

  public WordpieceTokenizer(Set<String> vocabulary) {
    this.vocabulary = vocabulary;
  }

  public WordpieceTokenizer(Set<String> vocabulary, int maxTokenLength) {
    this.vocabulary = vocabulary;
    this.maxTokenLength = maxTokenLength;
  }

  @Override
  public Span[] tokenizePos(final String text) {
    // TODO: Implement this.
    return null;
  }

  @Override
  public String[] tokenize(final String text) {

    final List<String> tokens = new LinkedList<>();
    tokens.add(CLASSIFICATION_TOKEN);

    // Put spaces around punctuation.
    final String spacedPunctuation = text.replaceAll("\\p{Punct}+", " $0 ");

    // Split based on whitespace.
    final String[] split = WhitespaceTokenizer.INSTANCE.tokenize(spacedPunctuation);

    // For each resulting word, if the word is found in the WordPiece vocabulary, keep it as-is.
    // If not, starting from the beginning, pull off the biggest piece that is in the vocabulary,
    // and prefix "##" to the remaining piece. Repeat until the entire word is represented by
    // pieces from the vocabulary, if possible.
    for (final String token : split) {

      final char[] characters = token.toCharArray();

      if (characters.length <= maxTokenLength) {

        // To start, the substring is the whole token.
        int start = 0;
        int end;

        // Look at the token from the start.
        while (start < characters.length) {

          end = characters.length;
          boolean found = false;

          // Look at the token from the end until the end is equal to the start.
          while (start < end) {

            // The substring is the part of the token we are looking at now.
            String substring = String.valueOf(characters, start, end - start);

            // This is a substring so prefix it with ##.
            if (start > 0) {
              substring = "##" + substring;
            }

            // See if the substring is in the vocabulary.
            if (vocabulary.contains(substring)) {

              // It is in the vocabulary so add it to the list of tokens.
              tokens.add(substring);

              // Next time we can pick up where we left off.
              start = end;
              found = true;

              break;

            }

            // Subtract 1 from the end to find the next longest piece in the vocabulary.
            end--;

          }

          // If the word can't be represented by vocabulary pieces replace
          // it with a specified "unknown" token.
          if (!found) {
            tokens.add(UNKNOWN_TOKEN);
            break;
          }

          // Start the next characters where we just left off.
          start = end;

        }

      } else {

        // If the token's length is greater than the max length just add [UNK] instead.
        tokens.add(UNKNOWN_TOKEN);

      }

    }

    tokens.add(SEPARATOR_TOKEN);

    return tokens.toArray(new String[0]);

  }

  public int getMaxTokenLength() {
    return maxTokenLength;
  }

}
