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
package opennlp.tools.wordnet;

import java.util.List;
import java.util.Optional;

/**
 * Lemma and synset lookup over a loaded lexical-semantic resource in the WordNet family. Synset
 * identity is opaque and source-qualified (see {@link Synset#id()}). Lookups return their matches
 * in the source's sense order. Lookup results are non-{@code null} lists.
 *
 * <p>How a queried lemma is matched against the source's written forms is implementation
 * specific and documented there. Returned {@link Synset#lemmas() lemmas} preserve the source's
 * written forms, with spaces in multiword lemmas.</p>
 *
 * <p>Thread safety is implementation specific.</p>
 *
 * @since 3.0.0
 */
public interface LexicalKnowledgeBase {

  /**
   * Finds the synsets containing a lemma with a part of speech, in the source's sense order
   * (the most salient sense first when the source ranks senses).
   *
   * @param lemma The lemma to look up. Must not be {@code null}.
   * @param pos   The part of speech to look it up as. Must not be {@code null}.
   * @return The matching synsets, never {@code null}; empty when the lexicon does not contain
   *     the lemma with that part of speech.
   * @throws IllegalArgumentException Thrown if {@code lemma} or {@code pos} is {@code null}.
   */
  List<Synset> lookup(String lemma, WordNetPOS pos);

  /**
   * Finds a synset by its opaque identifier.
   *
   * @param synsetId The synset identifier, as minted by this lexicon. Must not be {@code null}.
   * @return The synset, or empty when this lexicon has no synset with that identifier.
   * @throws IllegalArgumentException Thrown if {@code synsetId} is {@code null}.
   */
  Optional<Synset> synset(String synsetId);

  /**
   * Navigates one typed relation from a synset.
   *
   * @param synsetId The source synset identifier. Must not be {@code null}.
   * @param relation The relation type to follow. Must not be {@code null}.
   * @return The target synset ids in source order, never {@code null}; empty when the synset is
   *     unknown or has no relation of that type.
   * @throws IllegalArgumentException Thrown if {@code synsetId} or {@code relation} is
   *     {@code null}.
   */
  default List<String> related(String synsetId, WordNetRelation relation) {
    if (synsetId == null) {
      throw new IllegalArgumentException("synsetId must not be null");
    }
    if (relation == null) {
      throw new IllegalArgumentException("relation must not be null");
    }
    return synset(synsetId).map(s -> s.related(relation)).orElse(List.of());
  }

  /**
   * Tests whether the lexicon contains a lemma with a part of speech. The default implementation
   * delegates to {@link #lookup(String, WordNetPOS)}.
   *
   * @param lemma The lemma to test. Must not be {@code null}.
   * @param pos   The part of speech to test it as. Must not be {@code null}.
   * @return {@code true} if the lexicon contains the lemma with that part of speech.
   * @throws IllegalArgumentException Thrown if {@code lemma} or {@code pos} is {@code null}.
   */
  default boolean contains(String lemma, WordNetPOS pos) {
    if (lemma == null) {
      throw new IllegalArgumentException("lemma must not be null");
    }
    if (pos == null) {
      throw new IllegalArgumentException("pos must not be null");
    }
    return !lookup(lemma, pos).isEmpty();
  }
}
