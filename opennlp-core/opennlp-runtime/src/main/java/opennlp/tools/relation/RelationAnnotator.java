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

import opennlp.tools.depparse.DependencyAnnotator;
import opennlp.tools.depparse.DependencyArc;
import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.DocumentAnnotator;
import opennlp.tools.document.LayerKey;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;
import opennlp.tools.util.StringUtil;

/**
 * Extracts typed relations between entity pairs by matching {@link RelationPattern}
 * rules against the dependency path connecting the two entity heads, and provides
 * {@link #RELATIONS}, one annotation per relation carrying its {@link RelationMention}.
 *
 * <p>Each entity's head is the first token overlapping the entity span whose dependency
 * head lies outside the range of overlapping tokens. For every ordered entity pair the
 * annotator computes the path from
 * the subject's head up to the lowest common ancestor and down to the object's head,
 * then emits one relation per pattern whose path shape and trigger match. The annotation
 * covers both entity spans; the mention references the entities by their index in
 * {@link Layers#ENTITIES}.</p>
 *
 * <p>The annotator holds no per-call state and is safe to share between threads.</p>
 *
 * @since 3.0.0
 */
public class RelationAnnotator implements DocumentAnnotator {

  /**
   * Extracted relations; each annotation covers both entity spans and carries its
   * {@link RelationMention}.
   */
  public static final LayerKey<RelationMention> RELATIONS =
      LayerKey.of("relations", RelationMention.class);

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
    this.patternSteps = new ArrayList<>(this.patterns.size());
    for (final RelationPattern pattern : this.patterns) {
      patternSteps.add(pattern.steps());
    }
  }

  @Override
  public Document annotate(Document document) {
    if (document == null) {
      throw new IllegalArgumentException("document must not be null");
    }
    final List<Annotation<String>> tokens = document.get(Layers.TOKENS);
    final List<Annotation<String>> entities = document.get(Layers.ENTITIES);
    final List<Annotation<DependencyArc>> arcs =
        document.get(DependencyAnnotator.DEPENDENCIES);
    if (tokens.isEmpty() || arcs.size() != tokens.size()) {
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

    final int[] entityHeads = new int[entities.size()];
    for (int e = 0; e < entities.size(); e++) {
      entityHeads[e] = entityHead(entities.get(e).span(), tokens, heads);
    }

    final List<Annotation<RelationMention>> mentions = new ArrayList<>();
    for (int subject = 0; subject < entities.size(); subject++) {
      for (int object = 0; object < entities.size(); object++) {
        if (subject == object
            || entityHeads[subject] < 0 || entityHeads[object] < 0
            || entityHeads[subject] == entityHeads[object]) {
          continue;
        }
        matchPair(tokens, heads, relations, entities,
            subject, object, entityHeads, mentions);
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
   * Matches all patterns against one ordered entity pair and collects the resulting
   * relations. The pair contributes one relation per matching pattern; nothing is added
   * when the two heads are not connected or the arcs contain a cycle.
   *
   * @param tokens The token layer.
   * @param heads The dependency head of each token, indexed by dependent token;
   *              {@link DependencyArc#ROOT_HEAD} marks the sentence root.
   * @param relations The relation label of each token's arc to its dependency head,
   *                  indexed by dependent token.
   * @param entities The entity layer.
   * @param subject The subject entity index.
   * @param object The object entity index.
   * @param entityHeads The head token of each entity, or {@code -1} where an entity
   *                    overlaps no token.
   * @param mentions The list that receives one annotation per matching pattern.
   */
  private void matchPair(List<Annotation<String>> tokens,
      int[] heads, String[] relations, List<Annotation<String>> entities,
      int subject, int object, int[] entityHeads,
      List<Annotation<RelationMention>> mentions) {
    final List<Integer> subjectChain = chainToRoot(entityHeads[subject], heads);
    final List<Integer> objectChain = chainToRoot(entityHeads[object], heads);
    if (subjectChain == null || objectChain == null) {
      return;
    }
    int pivotOnSubject = -1;
    int pivotOnObject = -1;
    for (int o = 0; o < objectChain.size() && pivotOnSubject < 0; o++) {
      final int index = subjectChain.indexOf(objectChain.get(o));
      if (index >= 0) {
        pivotOnSubject = index;
        pivotOnObject = o;
      }
    }
    if (pivotOnSubject < 0) {
      return;
    }

    final List<String> steps = new ArrayList<>();
    for (int s = 0; s < pivotOnSubject; s++) {
      steps.add("<" + relations[subjectChain.get(s)]);
    }
    for (int o = pivotOnObject - 1; o >= 0; o--) {
      steps.add(">" + relations[objectChain.get(o)]);
    }
    final int pivot = subjectChain.get(pivotOnSubject);
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
   * Finds the head token of an entity: the first token overlapping the entity span
   * whose dependency head lies outside the index range of the overlapping tokens. A
   * token overlaps the entity when their spans share at least one character. When no
   * overlapping token is headed outside that range, which only cyclic arcs inside the
   * range can cause, the first overlapping token is used as a fallback.
   *
   * @param entity The entity span in text coordinates.
   * @param tokens The token layer.
   * @param heads The dependency head of each token, indexed by dependent token.
   * @return The head token index, or {@code -1} if no token overlaps the entity.
   */
  private static int entityHead(Span entity, List<Annotation<String>> tokens, int[] heads) {
    int first = -1;
    int last = -1;
    for (int t = 0; t < tokens.size(); t++) {
      final Span span = tokens.get(t).span();
      if (span.getStart() < entity.getEnd() && span.getEnd() > entity.getStart()) {
        if (first < 0) {
          first = t;
        }
        last = t;
      }
    }
    if (first < 0) {
      return -1;
    }
    for (int t = first; t <= last; t++) {
      if (heads[t] < first || heads[t] > last) {
        return t;
      }
    }
    return first;
  }

  /**
   * Walks from a token to the root, collecting the visited tokens in order.
   *
   * @param start The token to start from.
   * @param heads The dependency head of each token, indexed by dependent token.
   * @return The chain including {@code start} and ending at the root token, or
   *         {@code null} when the walk takes more steps than there are tokens, which
   *         only happens when the arcs contain a cycle.
   */
  private static List<Integer> chainToRoot(int start, int[] heads) {
    final List<Integer> chain = new ArrayList<>();
    int current = start;
    while (current != DependencyArc.ROOT_HEAD) {
      if (chain.size() > heads.length) {
        return null;
      }
      chain.add(current);
      current = heads[current];
    }
    return chain;
  }

}
