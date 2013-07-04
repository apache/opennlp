/*
 * Copyright 2013 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opennlp.tools.entitylinker;

import java.util.List;
import opennlp.tools.util.Span;

/**
 * EntityLinkers establish connections to external data to enrich extracted
 * entities. For instance, for Location entities a linker can be
 * developed to lookup each found location in a geonames gazateer. Another
 * example may be to find peoples' names and look them up in a database or active
 * directory. Intended to return n best matches for any give search, but can
 * also be implemented as deterministic
 *
 * @param <T> A type that extends Span
 *
 */
public interface EntityLinker<T extends Span> {

  /**
   *
   * @param text      the document text to be used as additional context, and to
   *                  derive sentences and tokens String[]
   * @param sentences the list of sentences spans that correspond to the text.
   * @param tokens    the spans that correspond to one of the sentences.
   * @param nameSpans the named entity spans that correspond to the tokens
   * @return
   */
  List<T> find(String text, Span sentences[], Span tokens[], Span nameSpans[]);

  /**
   * Links the names that correspond to the tokens[] spans. The sentenceindex
   * can be used to get the sentence text and tokens from the text based on the sentence and token spans.
   * The text is available for additional context.
   *
   * @param text          the document text to be used as additional context,
   *                      and to derive sentences and tokens String[]
   * @param sentences     the list of sentences spans that correspond to the
   *                      text.
   * @param tokens        the spans that correspond to one of the sentences.
   * @param nameSpans     the named entity spans that correspond to the tokens
   * @param sentenceIndex the index to the sentence span that the tokens[]
   *                      Span[] corresponds to
   * @return
   */
  List<T> find(String text, Span sentences[], Span tokens[], Span nameSpans[], int sentenceIndex);

  /**
   * Links the names that correspond to the tokens[]. The Sentences and text are
   * available for additional context.
   *
   * @param text      the document text to be used as additional context, and to
   *                  derive sentences and tokens String[]
   * @param sentences the list of sentences spans that correspond to the text.
   * @param tokens    the actual String[] of tokens that correspond to one of
   *                  the sentences.
   * @param nameSpans the named entity spans that correspond to the tokens
   * @return
   */
  List<T> find(String text, Span sentences[], String tokens[], Span nameSpans[]);
}
