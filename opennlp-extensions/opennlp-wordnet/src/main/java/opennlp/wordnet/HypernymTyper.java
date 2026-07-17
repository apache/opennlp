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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import opennlp.tools.util.StringUtil;
import opennlp.tools.wordnet.LexicalKnowledgeBase;
import opennlp.tools.wordnet.Synset;
import opennlp.tools.wordnet.WordNetPOS;
import opennlp.tools.wordnet.WordNetRelation;

/**
 * Types a noun by walking its hypernym chain to the nearest registered anchor: the
 * caller names anchor concepts by lemma, {@code person}, {@code organization},
 * {@code location}, and any word whose senses lead up to an anchor's synsets receives
 * that anchor's label. The nearest anchor wins, so a more specific registered concept
 * beats a general one.
 *
 * <p>Anchors are resolved against the knowledge base at construction and follow its
 * sense inventory; nothing beyond the caller's anchor choice is built in. Words with no
 * sense reaching an anchor get no type.</p>
 *
 * <p>The typer reads only immutable state and is safe to share between threads.</p>
 *
 * @since 3.0.0
 */
public class HypernymTyper {

  /** The relations that lead from a synset to its generalizations. */
  private static final List<WordNetRelation> UPWARD_RELATIONS =
      List.of(WordNetRelation.HYPERNYM, WordNetRelation.INSTANCE_HYPERNYM);

  private final LexicalKnowledgeBase knowledgeBase;
  private final Map<String, String> labelBySynsetId;

  /**
   * Initializes the typer.
   *
   * @param knowledgeBase The knowledge base to walk. Must not be {@code null}.
   * @param anchors The anchor lemmas mapped to the labels they confer, for example
   *                {@code person} to {@code person}. Every lemma is resolved as a noun;
   *                all its senses anchor. Must not be {@code null} or empty, and no
   *                lemma or label may be blank.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null},
   *         {@code anchors} is empty or holds a blank entry, or an anchor lemma is
   *         unknown to the knowledge base.
   */
  public HypernymTyper(LexicalKnowledgeBase knowledgeBase, Map<String, String> anchors) {
    if (knowledgeBase == null) {
      throw new IllegalArgumentException("knowledgeBase must not be null");
    }
    if (anchors == null || anchors.isEmpty()) {
      throw new IllegalArgumentException("anchors must not be null or empty");
    }
    this.knowledgeBase = knowledgeBase;
    final Map<String, String> labels = new LinkedHashMap<>();
    for (final Map.Entry<String, String> anchor : anchors.entrySet()) {
      if (anchor.getKey() == null || StringUtil.isBlank(anchor.getKey())
          || anchor.getValue() == null || StringUtil.isBlank(anchor.getValue())) {
        throw new IllegalArgumentException("anchors must not contain blank entries");
      }
      final List<Synset> senses = knowledgeBase.lookup(anchor.getKey(), WordNetPOS.NOUN);
      if (senses.isEmpty()) {
        throw new IllegalArgumentException(
            "anchor lemma is unknown to the knowledge base: " + anchor.getKey());
      }
      for (final Synset sense : senses) {
        labels.putIfAbsent(sense.id(), anchor.getValue());
      }
    }
    this.labelBySynsetId = Map.copyOf(labels);
  }

  /**
   * Types a noun by its nearest anchored hypernym.
   *
   * @param lemma The noun lemma to type. Must not be {@code null} or blank.
   * @return The label of the nearest anchor over all senses, or empty when no sense
   *         reaches an anchor.
   * @throws IllegalArgumentException Thrown if {@code lemma} is {@code null} or blank.
   */
  public Optional<String> type(String lemma) {
    if (lemma == null || StringUtil.isBlank(lemma)) {
      throw new IllegalArgumentException("lemma must not be null or blank");
    }
    String bestLabel = null;
    int bestDistance = Integer.MAX_VALUE;
    for (final Synset sense : knowledgeBase.lookup(lemma, WordNetPOS.NOUN)) {
      final int[] distance = new int[1];
      final String label = nearestAnchor(sense.id(), distance);
      if (label != null && distance[0] < bestDistance) {
        bestDistance = distance[0];
        bestLabel = label;
      }
    }
    return Optional.ofNullable(bestLabel);
  }

  /**
   * Types a specific synset by its nearest anchored hypernym.
   *
   * @param synsetId The synset identifier. Must not be {@code null}.
   * @return The nearest anchor's label, or empty when no ancestor is anchored.
   * @throws IllegalArgumentException Thrown if {@code synsetId} is {@code null}.
   */
  public Optional<String> typeSynset(String synsetId) {
    if (synsetId == null) {
      throw new IllegalArgumentException("synsetId must not be null");
    }
    return Optional.ofNullable(nearestAnchor(synsetId, new int[1]));
  }

  /** Breadth-first walk up the hypernym graph to the closest anchored synset. */
  private String nearestAnchor(String synsetId, int[] distanceOut) {
    final Set<String> visited = new HashSet<>();
    final Deque<String> queue = new ArrayDeque<>();
    final Map<String, Integer> depths = new HashMap<>();
    queue.add(synsetId);
    visited.add(synsetId);
    depths.put(synsetId, 0);
    while (!queue.isEmpty()) {
      final String current = queue.remove();
      final String label = labelBySynsetId.get(current);
      if (label != null) {
        distanceOut[0] = depths.get(current);
        return label;
      }
      for (final WordNetRelation relation : UPWARD_RELATIONS) {
        for (final String parent : knowledgeBase.related(current, relation)) {
          if (visited.add(parent)) {
            depths.put(parent, depths.get(current) + 1);
            queue.add(parent);
          }
        }
      }
    }
    return null;
  }
}
