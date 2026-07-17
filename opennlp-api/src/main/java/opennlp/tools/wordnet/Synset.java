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

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.commons.ThreadSafe;

/**
 * One synonym set: a single lexicalized concept with its member lemmas, gloss, and typed
 * relations to other synsets.
 *
 * <p>The {@link #id() id} is an opaque, source-qualified string minted by the reader that
 * produced the synset; consumers must not parse it, only pass it back to
 * {@link LexicalKnowledgeBase#synset(String)} and compare it for equality. Relations map each
 * {@link WordNetRelation} present on this synset to the target synset ids in source order.</p>
 *
 * <p>Instances are immutable and thread-safe: the list and map components are defensively
 * copied to immutable views at construction.</p>
 *
 * @param id        The opaque, source-qualified synset identifier. Must not be {@code null} or
 *                  empty.
 * @param pos       The part of speech. Must not be {@code null}.
 * @param lemmas    The member lemmas in source order, human-readable (multiword lemmas use
 *                  spaces, not the underscores some formats store). Must not be {@code null} or
 *                  empty, and must not contain {@code null} or empty elements.
 * @param gloss     The definition text, possibly empty when the source has none. Must not be
 *                  {@code null}.
 * @param relations The typed relations, each mapping to the target synset ids in source order.
 *                  Must not be {@code null}; keys must not be {@code null}; each value must be
 *                  a non-empty list of non-{@code null}, non-empty target ids.
 */
@ThreadSafe
public record Synset(
    String id,
    WordNetPOS pos,
    List<String> lemmas,
    String gloss,
    Map<WordNetRelation, List<String>> relations) {

  /**
   * Creates a synset.
   *
   * @throws IllegalArgumentException Thrown if any component violates its documented constraint.
   */
  public Synset {
    if (id == null || id.isEmpty()) {
      throw new IllegalArgumentException("Id must not be null or empty");
    }
    if (pos == null) {
      throw new IllegalArgumentException("Pos must not be null");
    }
    if (lemmas == null || lemmas.isEmpty()) {
      throw new IllegalArgumentException("Lemmas must not be null or empty for synset " + id);
    }
    for (final String lemma : lemmas) {
      if (lemma == null || lemma.isEmpty()) {
        throw new IllegalArgumentException(
            "Lemmas must not contain a null or empty element for synset " + id);
      }
    }
    if (gloss == null) {
      throw new IllegalArgumentException("Gloss must not be null for synset " + id);
    }
    if (relations == null) {
      throw new IllegalArgumentException("Relations must not be null for synset " + id);
    }
    final Map<WordNetRelation, List<String>> copiedRelations =
        new EnumMap<>(WordNetRelation.class);
    for (final Map.Entry<WordNetRelation, List<String>> relation : relations.entrySet()) {
      if (relation.getKey() == null) {
        throw new IllegalArgumentException("Relations must not contain a null key for synset " + id);
      }
      final List<String> targets = relation.getValue();
      if (targets == null || targets.isEmpty()) {
        throw new IllegalArgumentException("Relation " + relation.getKey()
            + " must map to a non-empty target list for synset " + id);
      }
      for (final String target : targets) {
        if (target == null || target.isEmpty()) {
          throw new IllegalArgumentException("Relation " + relation.getKey()
              + " must not contain a null or empty target id for synset " + id);
        }
      }
      copiedRelations.put(relation.getKey(), List.copyOf(targets));
    }
    lemmas = List.copyOf(lemmas);
    relations = Collections.unmodifiableMap(copiedRelations);
  }

  /**
   * Finds the target synset ids of one relation type.
   *
   * @param relation The relation type. Must not be {@code null}.
   * @return The target synset ids in source order, never {@code null}; empty when this synset
   *     has no relation of that type.
   * @throws IllegalArgumentException Thrown if {@code relation} is {@code null}.
   */
  public List<String> related(WordNetRelation relation) {
    if (relation == null) {
      throw new IllegalArgumentException("Relation must not be null");
    }
    final List<String> targets = relations.get(relation);
    return targets == null ? List.of() : targets;
  }
}
