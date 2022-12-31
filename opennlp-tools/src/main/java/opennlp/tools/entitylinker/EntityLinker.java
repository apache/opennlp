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

package opennlp.tools.entitylinker;

import java.io.IOException;
import java.util.List;

import opennlp.tools.util.Span;

/**
 * EntityLinkers establish connections with external data to enrich extracted
 * entities.
 * <p>
 * For instance, for Location entities a linker can be developed to
 * look up each found location in a geonames gazetteer. Another example may be to
 * find peoples' names and look them up in a database or active directory.
 * Intended to return n best matches for any given search, but can also be
 * implemented as deterministic.
 *
 * @param <T> A type that extends {@link Span}. {@link LinkedSpan} and {@link BaseLink}
 *            are available to provide this signature. Use:
 *            {@link EntityLinker}&lt;LinkedSpan&lt;BaseLink&gt;&gt; as a default.
 */
public interface EntityLinker<T extends Span> {

  /**
   * Initializes an {@link EntityLinker} and allows for passing properties
   * through the {@link EntityLinkerFactory} into all impls dynamically.
   * <p>
   * {@link EntityLinker} impls should initialize reusable objects
   * used by the impl in this method. If this is done, any errors will be
   * captured and thrown by the {@link EntityLinkerFactory}.
   *
   * @param initializationData The {@link EntityLinkerProperties} that contains
   *                           properties needed by the impl, as well as any
   *                           other objects required.
   * @throws IOException Thrown if IO errors occurred.
   */
  void init(EntityLinkerProperties initializationData) throws IOException;

  /**
   * Links an entire document of named entities to an external source.
   *
   * @param doctext          The full text of the document.
   * @param sentences        An array of {@link Span sentence spans}.
   * @param tokensBySentence An array of {@link Span tokens spans} that correspond to
   *                         each sentence. The outer array refers to the sentence, the inner
   *                         array is the tokens for the outer sentence. Similar
   *                         in nature to Map of SentenceIndex keys to List of
   *                         tokens as values.
   * @param namesBySentence  An array of {@link Span name spans} that correspond to each
   *                         sentence. The outer array refers to the sentence,
   *                         the inner array refers to the tokens that for the
   *                         same sentence. Similar in nature to
   *                         Map&lt;SentenceIndex,List&lt;Name Spans For This
   *                         Sentence's Tokens&gt;&gt; @ return.
   *
   * @return A list of {@link T} instances.
   */
  List<T> find(String doctext, Span[] sentences, Span[][] tokensBySentence, Span[][] namesBySentence);


  /**
   * Links the names that correspond to the tokens[] spans. The {@code sentenceIndex}
   * can be used to get the sentence text and tokens from the text based on the
   * sentence and token spans. The text is available for additional context.
   *
   * @param doctext          The full text of the document.
   * @param sentences        An array of {@link Span sentence spans}.
   * @param tokensBySentence An array of {@link Span tokens spans} that correspond to each
   *                         sentence. The outer array refers to the sentence, the inner
   *                         array is the tokens for the outer sentence. Similar
   *                         in nature to Map of SentenceIndex keys to List of
   *                         tokens as values.
   * @param namesBySentence  An array of {@link Span name spans} that correspond to each
   *                         sentence. The outer array refers to the sentence,
   *                         the inner array refers to the tokens that for the
   *                         same sentence. Similar in nature to
   *                         Map&lt;SentenceIndex,List&lt;Name Spans For This
   *                         Sentence's Tokens&gt;&gt; @ return.
   * @param sentenceIndex The index to the sentence span that the {@code tokensBySentence}
   *                      corresponds to.
   *
   * @return A list of {@link T} instances.
   */
  List<T> find(String doctext, Span[] sentences, Span[][] tokensBySentence,
      Span[][] namesBySentence, int sentenceIndex);
}
