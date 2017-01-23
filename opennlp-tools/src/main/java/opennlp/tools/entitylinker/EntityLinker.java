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
 * EntityLinkers establish connections to external data to enrich extracted
 * entities. For instance, for Location entities a linker can be developed to
 * lookup each found location in a geonames gazateer. Another example may be to
 * find peoples' names and look them up in a database or active directory.
 * Intended to return n best matches for any give search, but can also be
 * implemented as deterministic
 *
 * @param <T> A type that extends Span. LinkedSpan and BaseLink are provided to
 *            provide this signature: EntityLinker&lt;LinkedSpan&lt;BaseLink&gt;&gt; as a
 *            default
 */
public interface EntityLinker<T extends Span> {

  /**
   * allows for passing properties through the EntityLinkerFactory into all
   * impls dynamically. EntityLinker impls should initialize reusable objects
   * used by the impl in this method. If this is done, any errors will be
   * captured and thrown by the EntityLinkerFactory.
   *
   * @param initializationData the EntityLinkerProperties object that contains
   *                           properties needed by the impl, as well as any
   *                           other objects required for the impl
   * @throws java.io.IOException
   */
  void init(EntityLinkerProperties initializationData) throws IOException;

  /**
   * Links an entire document of named entities to an external source
   *
   * @param doctext          the full text of the document
   * @param tokensBySentence a list of tokens spans that correspond to each sentence.
   *                         The outer array refers to the sentence, the inner
   *                         array is the tokens for the outer sentence. Similar
   *                         in nature to Map of SentenceIndex keys to Listof
   *                         tokens as values
   * @param namesBySentence  a list of name spans that correspond to each
   *                         sentence. The outer array refers to the sentence,
   *                         the inner array refers to the tokens that for the
   *                         same sentence.Similar in nature to
   *                         Map&lt;SentenceIndex,List&lt;Name Spans For This
   *                         Sentence's Tokens&gt;&gt; @ return
   * @return
   */
  List<T> find(String doctext, Span[] sentences, Span[][] tokensBySentence, Span[][] namesBySentence);


  /**
   * Links the names that correspond to the tokens[] spans. The sentenceindex
   * can be used to get the sentence text and tokens from the text based on the
   * sentence and token spans. The text is available for additional context.
   *
   * @param doctext          the full text of the document
   * @param tokensBySentence a list of tokens spans that correspond to each sentence.
   *                         The outer array refers to the sentence, the inner
   *                         array is the tokens for the outer sentence. Similar
   *                         in nature to Map of SentenceIndex keys to Listof
   *                         tokens as values
   * @param namesBySentence  a list of name spans that correspond to each
   *                         sentence. The outer array refers to the sentence,
   *                         the inner array refers to the tokens that for the
   *                         same sentence.Similar in nature to
   *                         Map&lt;SentenceIndex,List&lt;Name Spans For This
   *                         Sentence's Tokens&gt;&gt; @ return
   * @param sentenceIndex the index to the sentence span that the tokens[]
   *                      Span[] corresponds to
   * @return
   */
  List<T> find(String doctext, Span[] sentences, Span[][] tokensBySentence,
      Span[][] namesBySentence, int sentenceIndex);
}
