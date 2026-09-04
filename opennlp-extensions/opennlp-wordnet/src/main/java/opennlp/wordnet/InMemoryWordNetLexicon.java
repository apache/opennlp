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
package opennlp.wordnet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;

/**
 * The immutable in-memory {@link LexicalKnowledgeBase} both readers produce: a synset table plus a
 * folded (lemma, part of speech) index. Package-private because it is a reader product, not a
 * public entry point; consumers hold it as {@link LexicalKnowledgeBase}.
 *
 * <p>Keys and queries are folded identically (see {@link LemmaFolding}). Construction verifies
 * referential integrity: each relation target must resolve to a synset in the table. After
 * construction all state is immutable, making instances safe for concurrent lookups.</p>
 */
@ThreadSafe
final class InMemoryWordNetLexicon implements LexicalKnowledgeBase {

  private final Map<String, Synset> synsetsById;
  private final Map<LemmaKey, List<Synset>> senseIndex;

  /**
   * Indexes the given synsets.
   *
   * @param synsetsById The synset table keyed by synset id; every key must equal its synset's
   *                    {@link Synset#id() id}. Must not be {@code null}.
   * @param senseOrder  The sense order per folded (lemma, part of speech) key: for each key the
   *                    ids of the synsets containing the lemma, most salient sense first, each
   *                    id resolvable in {@code synsetsById} and free of duplicates per key.
   *                    Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if a relation target or sense entry does not
   *     resolve, or a key disagrees with its synset id.
   */
  InMemoryWordNetLexicon(Map<String, Synset> synsetsById, Map<LemmaKey, List<String>> senseOrder) {
    if (synsetsById == null) {
      throw new IllegalArgumentException("synsetsById must not be null");
    }
    if (senseOrder == null) {
      throw new IllegalArgumentException("senseOrder must not be null");
    }
    final Map<String, Synset> byId = HashMap.newHashMap(synsetsById.size());
    for (final Map.Entry<String, Synset> entry : synsetsById.entrySet()) {
      if (entry.getValue() == null || !entry.getValue().id().equals(entry.getKey())) {
        throw new IllegalArgumentException(
            "Synset table key " + entry.getKey() + " does not match its synset");
      }
      byId.put(entry.getKey(), entry.getValue());
    }
    for (final Synset synset : byId.values()) {
      for (final Map.Entry<WordNetRelation, List<String>> relation :
          synset.relations().entrySet()) {
        for (final String target : relation.getValue()) {
          if (!byId.containsKey(target)) {
            throw new IllegalArgumentException("Synset " + synset.id() + " has a "
                + relation.getKey() + " relation to unknown synset " + target);
          }
        }
      }
    }
    final Map<LemmaKey, List<Synset>> index = HashMap.newHashMap(senseOrder.size());
    for (final Map.Entry<LemmaKey, List<String>> entry : senseOrder.entrySet()) {
      if (entry.getKey() == null) {
        throw new IllegalArgumentException("senseOrder key must not be null");
      }
      final LemmaKey key = entry.getKey();
      final List<String> orderedIds = entry.getValue();
      if (orderedIds == null) {
        throw new IllegalArgumentException("senseOrder value must not be null");
      }
      final List<Synset> senses = new ArrayList<>(orderedIds.size());
      final Set<String> seen = HashSet.newHashSet(orderedIds.size());
      for (final String synsetId : orderedIds) {
        if (!seen.add(synsetId)) {
          throw new IllegalArgumentException("Sense index entry " + entry.getKey().lemma()
              + " (" + entry.getKey().pos() + ") contains duplicate synset " + synsetId);
        }
        final Synset synset = byId.get(synsetId);
        if (synset == null) {
          throw new IllegalArgumentException("Sense index entry " + key.lemma()
              + " (" + key.pos() + ") references unknown synset " + synsetId);
        }
        boolean containsLemma = false;
        for (final String lemma : synset.lemmas()) {
          if (key.lemma().equals(LemmaFolding.fold(lemma))) {
            containsLemma = true;
            break;
          }
        }
        if (synset.pos() != key.pos() || !containsLemma) {
          throw new IllegalArgumentException("Sense index entry " + key.lemma()
              + " (" + key.pos() + ") references synset " + synsetId
              + ", which does not contain that lemma and part of speech");
        }
        senses.add(synset);
      }
      index.put(key, List.copyOf(senses));
    }
    this.synsetsById = byId;
    this.senseIndex = index;
  }

  /** {@inheritDoc} */
  @Override
  public List<Synset> lookup(String lemma, WordNetPOS pos) {
    if (lemma == null) {
      throw new IllegalArgumentException("lemma must not be null");
    }
    if (pos == null) {
      throw new IllegalArgumentException("pos must not be null");
    }
    final List<Synset> senses = senseIndex.get(LemmaKey.of(lemma, pos));
    return senses == null ? List.of() : senses;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Synset> synset(String synsetId) {
    if (synsetId == null) {
      throw new IllegalArgumentException("synsetId must not be null");
    }
    return Optional.ofNullable(synsetsById.get(synsetId));
  }

  /** {@return the number of synsets in this lexicon} */
  int size() {
    return synsetsById.size();
  }

  /** {@return all synsets, for equivalence checks and diagnostics within this package} */
  Collection<Synset> synsets() {
    return Collections.unmodifiableCollection(synsetsById.values());
  }

  /**
   * A folded sense-index key. Build with {@link #of(String, WordNetPOS)} to apply the same fold
   * to stored keys and queries.
   *
   * @param lemma The folded lemma.
   * @param pos   The part of speech.
   */
  record LemmaKey(String lemma, WordNetPOS pos) {

    LemmaKey {
      if (lemma == null) {
        throw new IllegalArgumentException("lemma must not be null");
      }
      if (pos == null) {
        throw new IllegalArgumentException("pos must not be null");
      }
    }

    /**
     * Folds a written form into a key using locale-independent lowercase and spaces for
     * underscores.
     *
     * @param writtenForm The lemma as written in the source or query. Must not be {@code null}.
     * @param pos         The part of speech. Must not be {@code null}.
     * @return The folded key.
     * @throws IllegalArgumentException Thrown if {@code writtenForm} is {@code null}.
     */
    static LemmaKey of(String writtenForm, WordNetPOS pos) {
      return new LemmaKey(LemmaFolding.fold(writtenForm), pos);
    }
  }
}
