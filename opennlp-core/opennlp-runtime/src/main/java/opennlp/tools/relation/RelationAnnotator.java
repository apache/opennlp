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

package opennlp.tools.relation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import opennlp.tools.commons.ThreadSafe;
import opennlp.tools.depparse.DependencyAnnotator;
import opennlp.tools.depparse.DependencyArc;
import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnnotator;
import opennlp.tools.document.DocumentAnnotators;
import opennlp.tools.document.LayerKey;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Extracts typed relations by matching {@link RelationPattern} rules against dependency
 * paths between entity heads. Each result covers both entity spans and carries a
 * {@link RelationMention} with indices into {@link Layers#ENTITIES}.
 *
 * <p>An entity head is the first overlapping token with a dependency head outside the
 * entity. For each ordered entity pair, matching follows the path through the lowest
 * common ancestor.</p>
 *
 * <p>Tokens need not be stored in text order. An overlap requires at least one shared
 * character, so empty spans do not select entity heads.</p>
 *
 * <p>The annotator holds no per-call state and is safe to share between threads.</p>
 *
 * @since 3.0.0
 */
@ThreadSafe
public final class RelationAnnotator implements DocumentAnnotator {

  private static final byte UNVISITED = 0;
  private static final byte VISITING = 1;
  private static final byte VISITED = 2;

  /**
   * Extracted relations; each annotation covers both entity spans and carries its
   * {@link RelationMention}.
   */
  public static final LayerKey<RelationMention> RELATIONS =
      Layers.key("relations", RelationMention.class);

  private final List<RelationPattern> patterns;
  private final List<List<String>> patternSteps;

  /**
   * Initializes the annotator.
   *
   * @param patterns The rules to match. Must not be {@code null} or empty, and no rule
   *                 may be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code patterns} is {@code null}, empty,
   *         or contains {@code null}.
   */
  public RelationAnnotator(Collection<RelationPattern> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      throw new IllegalArgumentException("patterns must not be null or empty");
    }
    for (final RelationPattern pattern : patterns) {
      if (pattern == null) {
        throw new IllegalArgumentException("patterns must not contain null");
      }
    }
    this.patterns = List.copyOf(patterns);
    final List<List<String>> steps = new ArrayList<>(this.patterns.size());
    for (final RelationPattern pattern : this.patterns) {
      steps.add(pattern.steps());
    }
    this.patternSteps = List.copyOf(steps);
  }

  /**
   * Matches every registered pattern against every ordered entity pair and adds the
   * {@link #RELATIONS} layer.
   *
   * <p>Pairs are visited in entity layer order and the patterns are applied in
   * registration order, so the extracted relations are in a stable order. A pair whose
   * entities share a head token, or whose heads are not connected in the dependency
   * graph, contributes no relation.</p>
   *
   * @param document The document to annotate. Must not be {@code null} and must carry
   *                 the {@link Layers#TOKENS}, {@link Layers#ENTITIES}, and
   *                 {@link DependencyAnnotator#DEPENDENCIES} layers, the dependency
   *                 layer holding exactly one arc per token. The layers may be empty: a
   *                 document without tokens or without entities yields an empty
   *                 {@link #RELATIONS} layer.
   * @return A new {@link Document} with the {@link #RELATIONS} layer added. Never
   *         {@code null}.
   * @throws IllegalArgumentException Thrown if {@code document} is {@code null}, a
   *         required layer is absent, the dependency layer does not hold exactly one
   *         arc per token, two arcs share a dependent, an arc refers to a token
   *         outside the token layer, the dependency layer contains a cycle, or the
   *         document already carries the
   *         {@link #RELATIONS} layer.
   */
  @Override
  public Document annotate(Document document) {
    DocumentAnnotators.requireLayers(document, Layers.TOKENS, Layers.ENTITIES,
        DependencyAnnotator.DEPENDENCIES);
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final List<Annotation<String>> entities = document.get(Layers.ENTITIES);
    final List<Annotation<DependencyArc>> arcs =
        document.get(DependencyAnnotator.DEPENDENCIES);
    if (arcs.size() != tokens.size()) {
      throw new IllegalArgumentException("document needs aligned " + Layers.TOKENS
          + " and " + DependencyAnnotator.DEPENDENCIES + " layers");
    }

    final int[] heads = new int[tokens.size()];
    final String[] relations = new String[tokens.size()];
    for (final Annotation<DependencyArc> arc : arcs) {
      final int dependent = arc.value().dependent();
      if (dependent >= tokens.size() || arc.value().head() >= tokens.size()
          || relations[dependent] != null) {
        throw new IllegalArgumentException(
            "dependency layer is not aligned with the token layer at " + dependent);
      }
      heads[dependent] = arc.value().head();
      relations[dependent] = arc.value().relation();
    }
    checkAcyclic(heads);

    final int[] entityHeads = new int[entities.size()];
    final int[][] chains = new int[entities.size()][];
    for (int e = 0; e < entities.size(); e++) {
      entityHeads[e] = entityHead(entities.get(e).span(), tokens, heads);
      chains[e] = entityHeads[e] < 0 ? null : chainToRoot(entityHeads[e], heads);
    }

    final List<Annotation<RelationMention>> mentions = new ArrayList<>();
    for (int subject = 0; subject < entities.size(); subject++) {
      for (int object = 0; object < entities.size(); object++) {
        if (subject == object || entityHeads[subject] == entityHeads[object]
            || chains[subject] == null || chains[object] == null) {
          continue;
        }
        matchPair(tokens, relations, entities, subject, object,
            chains[subject], chains[object], mentions);
      }
    }
    return document.with(RELATIONS, mentions);
  }

  @Override
  public Set<LayerKey<?>> requires() {
    return Set.of(Layers.TOKENS, Layers.ENTITIES, DependencyAnnotator.DEPENDENCIES);
  }

  @Override
  public Set<LayerKey<?>> provides() {
    return Set.of(RELATIONS);
  }

  /**
   * {@return the annotator's simple class name, which names it in pipeline validation
   * messages}
   */
  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

  /**
   * Matches all patterns against one ordered entity pair and collects the resulting
   * relations. The pair contributes one relation per matching pattern, and nothing when
   * the two chains do not meet.
   *
   * @param tokens The token layer.
   * @param relations The relation label of each token's arc to its dependency head,
   *                  indexed by dependent token.
   * @param entities The entity layer.
   * @param subject The subject entity index.
   * @param object The object entity index.
   * @param subjectChain The chain from the subject's head token to the root.
   * @param objectChain The chain from the object's head token to the root.
   * @param mentions The list that receives one annotation per matching pattern.
   */
  private void matchPair(List<Annotation<String>> tokens,
      String[] relations, List<Annotation<String>> entities,
      int subject, int object,
      int[] subjectChain, int[] objectChain,
      List<Annotation<RelationMention>> mentions) {
    int pivotOnSubject = -1;
    int pivotOnObject = -1;
    for (int o = 0; o < objectChain.length && pivotOnSubject < 0; o++) {
      for (int s = 0; s < subjectChain.length; s++) {
        if (subjectChain[s] == objectChain[o]) {
          pivotOnSubject = s;
          pivotOnObject = o;
          break;
        }
      }
    }
    if (pivotOnSubject < 0) {
      return;
    }

    final List<String> steps = new ArrayList<>();
    for (int s = 0; s < pivotOnSubject; s++) {
      steps.add(RelationPattern.UP_STEP + relations[subjectChain[s]]);
    }
    for (int o = pivotOnObject - 1; o >= 0; o--) {
      steps.add(RelationPattern.DOWN_STEP + relations[objectChain[o]]);
    }
    final int pivot = subjectChain[pivotOnSubject];
    final String pivotForm = StringUtil.toLowerCase(tokens.get(pivot).value());

    for (int p = 0; p < patterns.size(); p++) {
      final RelationPattern pattern = patterns.get(p);
      if (patternSteps.get(p).equals(steps)
          && (pattern.trigger() == null || pattern.trigger().equals(pivotForm))) {
        final Span subjectSpan = entities.get(subject).span();
        final Span objectSpan = entities.get(object).span();
        final Span covering = new Span(
            Math.min(subjectSpan.getStart(), objectSpan.getStart()),
            Math.max(subjectSpan.getEnd(), objectSpan.getEnd()));
        mentions.add(new Annotation<>(covering,
            new RelationMention(pattern.type(), subject, object)));
      }
    }
  }

  /**
   * Selects the first token in layer order that overlaps the entity and has a root
   * arc or a dependency head that does not overlap the entity. Token indexes need
   * not be contiguous within an entity.
   *
   * @param entity The entity span in text coordinates.
   * @param tokens The token layer.
   * @param heads The dependency head of each token, indexed by dependent token.
   * @return The head token index, or {@code -1} if no token overlaps the entity.
   */
  private int entityHead(Span entity, List<Annotation<String>> tokens, int[] heads) {
    for (int t = 0; t < tokens.size(); t++) {
      if (overlaps(entity, tokens.get(t).span())
          && (heads[t] == DependencyArc.ROOT_HEAD || !overlaps(entity, tokens.get(heads[t]).span()))) {
        return t;
      }
    }
    return -1;
  }

  /**
   * Checks whether an entity and token share at least one character.
   *
   * @param entity The entity span.
   * @param token The token span.
   * @return Whether the intersection has positive length.
   */
  private boolean overlaps(Span entity, Span token) {
    return Math.max(entity.getStart(), token.getStart()) < Math.min(entity.getEnd(), token.getEnd());
  }

  /** Rejects a cycle in the document dependency forest. */
  private static void checkAcyclic(int[] heads) {
    final byte[] states = new byte[heads.length];
    for (int start = 0; start < heads.length; start++) {
      int current = start;
      while (current != DependencyArc.ROOT_HEAD && states[current] == UNVISITED) {
        states[current] = VISITING;
        current = heads[current];
      }
      if (current != DependencyArc.ROOT_HEAD && states[current] == VISITING) {
        throw new IllegalArgumentException(
            "dependency layer contains a cycle at token " + current);
      }
      current = start;
      while (current != DependencyArc.ROOT_HEAD && states[current] == VISITING) {
        states[current] = VISITED;
        current = heads[current];
      }
    }
  }

  /**
   * Walks from a token to the root, collecting the visited tokens in order.
   *
   * @param start The token to start from.
   * @param heads The dependency head of each token, indexed by dependent token.
   * @return The chain including {@code start} and ending at the root token.
   */
  private static int[] chainToRoot(int start, int[] heads) {
    int length = 0;
    for (int current = start; current != DependencyArc.ROOT_HEAD; current = heads[current]) {
      length++;
    }
    final int[] chain = new int[length];
    int current = start;
    for (int i = 0; i < length; i++) {
      chain[i] = current;
      current = heads[current];
    }
    return chain;
  }

}
