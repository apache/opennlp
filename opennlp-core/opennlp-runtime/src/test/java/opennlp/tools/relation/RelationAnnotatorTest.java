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
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.depparse.DependencyAnnotator;
import opennlp.tools.depparse.DependencyArc;
import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;

/**
 * Verifies {@link RelationAnnotator} against a document with directly constructed token,
 * entity, and dependency layers, covering matching, direction handling, duplicate and
 * invalid configurations, and the required-layer contract for absent and empty layers.
 */
public class RelationAnnotatorTest {

  /** {@code Istanbul} with the Turkish capital I with dot above, U+0130, as its initial. */
  private static final String DOTTED_CAPITAL_ISTANBUL = "\u0130stanbul";

  /** {@code Istanbul} as the JDK lowercases U+0130: an i plus U+0307 combining dot above. */
  private static final String JDK_LOWERCASED_ISTANBUL = "i\u0307stanbul";

  /** A single no-break space, U+00A0, whitespace to the toolkit but not to the JDK. */
  private static final String NO_BREAK_SPACE = "\u00A0";

  /**
   * Anchors each dependency arc on its dependent token's span, the layout
   * {@link DependencyAnnotator} produces.
   *
   * @param tokens The token layer the arcs index into.
   * @param arcs The arcs to annotate.
   * @return One annotation per arc, in arc order. Never {@code null}.
   */
  private static List<Annotation<DependencyArc>> dependencyLayer(
      List<Annotation<String>> tokens, List<DependencyArc> arcs) {
    final List<Annotation<DependencyArc>> dependencies = new ArrayList<>(arcs.size());
    for (final DependencyArc arc : arcs) {
      dependencies.add(new Annotation<>(tokens.get(arc.dependent()).span(), arc));
    }
    return dependencies;
  }

  /**
   * Builds the parsed document "Acme Corp acquired Bolt in 2024." with entities for
   * Acme Corp (index 0), Bolt (index 1), and 2024 (index 2). The dependency layer marks
   * {@code acquired} as the root with {@code Corp} as {@code nsubj}, {@code Bolt} as
   * {@code obj}, and {@code 2024} as {@code obl}.
   *
   * @return A document with aligned token, entity, and dependency layers. Never
   *         {@code null}.
   */
  private static Document acquisitionDocument() {
    final String text = "Acme Corp acquired Bolt in 2024.";
    final List<Annotation<String>> tokens = List.of(
        new Annotation<>(new Span(0, 4), "Acme"),
        new Annotation<>(new Span(5, 9), "Corp"),
        new Annotation<>(new Span(10, 18), "acquired"),
        new Annotation<>(new Span(19, 23), "Bolt"),
        new Annotation<>(new Span(24, 26), "in"),
        new Annotation<>(new Span(27, 31), "2024"),
        new Annotation<>(new Span(31, 32), "."));
    final List<DependencyArc> arcs = List.of(
        new DependencyArc(1, 0, "compound"),
        new DependencyArc(2, 1, "nsubj"),
        new DependencyArc(DependencyArc.ROOT_HEAD, 2, "root"),
        new DependencyArc(2, 3, "obj"),
        new DependencyArc(5, 4, "case"),
        new DependencyArc(2, 5, "obl"),
        new DependencyArc(2, 6, "punct"));
    return Document.of(text)
        .with(Layers.TOKENS, tokens)
        .with(Layers.ENTITIES, List.of(
            new Annotation<>(new Span(0, 9), "organization"),
            new Annotation<>(new Span(19, 23), "organization"),
            new Annotation<>(new Span(27, 31), "date")))
        .with(DependencyAnnotator.DEPENDENCIES, dependencyLayer(tokens, arcs));
  }

  /**
   * Verifies the basic match: a subject-verb-object pattern with a trigger extracts
   * exactly one relation with the expected type, entity indexes, and covering span.
   */
  @Test
  void testMatchesSubjectVerbObjectPath() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", "acquired")));

    final Document document = annotator.annotate(acquisitionDocument());

    final List<Annotation<RelationMention>> relations =
        document.get(RelationAnnotator.RELATIONS);
    Assertions.assertEquals(1, relations.size());
    final Annotation<RelationMention> relation = relations.get(0);
    Assertions.assertEquals("acquisition", relation.value().type());
    Assertions.assertEquals(0, relation.value().subject());
    Assertions.assertEquals(1, relation.value().object());
    Assertions.assertEquals("Acme Corp acquired Bolt", document.text().subSequence(
        relation.span().getStart(), relation.span().getEnd()).toString());
  }

  /**
   * Verifies that a matching path shape alone is not enough when a trigger is set: a
   * pivot form different from the trigger suppresses the relation.
   */
  @Test
  void testTriggerMismatchDoesNotMatch() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", "bought")));

    Assertions.assertTrue(annotator.annotate(acquisitionDocument())
        .get(RelationAnnotator.RELATIONS).isEmpty());
  }

  /**
   * Verifies that a pattern matches in one direction only: the subject-verb-object
   * shape fires for the forward argument order and never for the reversed one.
   */
  @Test
  void testDirectionMatters() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", null)));

    final List<Annotation<RelationMention>> relations =
        annotator.annotate(acquisitionDocument()).get(RelationAnnotator.RELATIONS);

    Assertions.assertEquals(1, relations.size());
    Assertions.assertEquals(0, relations.get(0).value().subject());
    Assertions.assertEquals(1, relations.get(0).value().object());
  }

  /**
   * Verifies that independently registered patterns fire independently: two rules over
   * different entity pairs produce two relations in registration order.
   */
  @Test
  void testSeveralPatternsEmitSeveralRelations() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", "acquired"),
        new RelationPattern("acquired_in", "<obj >obl", null)));

    final List<Annotation<RelationMention>> relations =
        annotator.annotate(acquisitionDocument()).get(RelationAnnotator.RELATIONS);

    Assertions.assertEquals(2, relations.size());
    Assertions.assertEquals("acquisition", relations.get(0).value().type());
    Assertions.assertEquals("acquired_in", relations.get(1).value().type());
    Assertions.assertEquals(1, relations.get(1).value().subject());
    Assertions.assertEquals(2, relations.get(1).value().object());
  }

  /**
   * Verifies head selection for a multi-token entity: for "Acme Corp" the head is
   * {@code Corp}, the token attached outside the entity, so the {@code nsubj} step
   * starts there and the pattern still matches.
   */
  @Test
  void testMultiwordEntityHeadIsTheOutwardToken() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", "acquired")));

    final List<Annotation<RelationMention>> relations =
        annotator.annotate(acquisitionDocument()).get(RelationAnnotator.RELATIONS);

    Assertions.assertEquals(1, relations.size());
    Assertions.assertEquals(0, relations.get(0).value().subject());
  }

  /**
   * Verifies that malformed patterns are rejected at construction: wrong step order, a
   * step without a direction marker, a marker without a label, a blank type, and a
   * trigger that is not a single token.
   */
  @Test
  void testPatternValidation() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", ">obj <nsubj", null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", "nsubj", null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", "<", null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern(" ", "<nsubj", null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationPattern("t", "<nsubj", " "));
  }

  /**
   * Verifies that every invalid {@link RelationMention} component is rejected at
   * construction with the exact message: a {@code null}, blank, or no-break space only
   * type, a negative entity index, and a subject that equals its object. Blankness
   * follows the project whitespace definition, the same one a pattern's type is judged
   * by, so a type spelled from no-break spaces alone is content in neither.
   */
  @Test
  void testRelationMentionValidation() {
    final IllegalArgumentException nullType = Assertions.assertThrows(
        IllegalArgumentException.class, () -> new RelationMention(null, 0, 1));
    Assertions.assertEquals("type must not be null or blank", nullType.getMessage());

    final IllegalArgumentException blankType = Assertions.assertThrows(
        IllegalArgumentException.class, () -> new RelationMention(" ", 0, 1));
    Assertions.assertEquals("type must not be null or blank", blankType.getMessage());

    final IllegalArgumentException noBreakSpaceType = Assertions.assertThrows(
        IllegalArgumentException.class, () -> new RelationMention(NO_BREAK_SPACE, 0, 1));
    Assertions.assertEquals("type must not be null or blank", noBreakSpaceType.getMessage());

    final IllegalArgumentException negative = Assertions.assertThrows(
        IllegalArgumentException.class, () -> new RelationMention("t", 0, -1));
    Assertions.assertEquals("entity indexes must not be negative: 0, -1",
        negative.getMessage());

    final IllegalArgumentException same = Assertions.assertThrows(
        IllegalArgumentException.class, () -> new RelationMention("t", 1, 1));
    Assertions.assertEquals("subject and object must differ: 1", same.getMessage());
  }

  /**
   * Verifies that the annotator rejects invalid configurations and inputs: a
   * {@code null} or empty pattern collection, a {@code null} document, and a document
   * without the required token and dependency layers.
   */
  @Test
  void testInvalidArguments() {
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationAnnotator(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationAnnotator(List.of()));
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("t", "<nsubj", null)));
    Assertions.assertThrows(IllegalArgumentException.class, () -> annotator.annotate(null));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(Document.of("no layers")));
  }

  /**
   * Verifies that a path shape starting on the object arc binds the arguments the other
   * way around: {@code <obj >nsubj} fires exactly once, with {@code Bolt} as the subject
   * and {@code Acme Corp} as the object, and never for the forward argument order.
   */
  @Test
  void testReversedPathShapeBindsSwappedRoles() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<obj >nsubj", null)));

    final List<Annotation<RelationMention>> relations =
        annotator.annotate(acquisitionDocument()).get(RelationAnnotator.RELATIONS);

    Assertions.assertEquals(1, relations.size());
    Assertions.assertEquals(new RelationMention("acquisition", 1, 0),
        relations.get(0).value());
  }

  /**
   * Verifies that a pattern only matches the complete path between the two entity
   * heads: {@code <nsubj} alone is a strict prefix of every connecting path in the
   * document, so no relation is extracted.
   */
  @Test
  void testPathLongerThanPatternDoesNotMatch() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("t", "<nsubj", null)));

    Assertions.assertTrue(annotator.annotate(acquisitionDocument())
        .get(RelationAnnotator.RELATIONS).isEmpty());
  }

  /**
   * Builds the parsed document "Ada slept." with token and dependency layers only, so
   * tests can attach an entity layer or leave it absent.
   *
   * @return A document without an entity layer. Never {@code null}.
   */
  private static Document sleepingDocument() {
    final String text = "Ada slept.";
    final List<Annotation<String>> tokens = List.of(
        new Annotation<>(new Span(0, 3), "Ada"),
        new Annotation<>(new Span(4, 9), "slept"),
        new Annotation<>(new Span(9, 10), "."));
    final List<DependencyArc> arcs = List.of(
        new DependencyArc(1, 0, "nsubj"),
        new DependencyArc(DependencyArc.ROOT_HEAD, 1, "root"),
        new DependencyArc(1, 2, "punct"));
    return Document.of(text)
        .with(Layers.TOKENS, tokens)
        .with(DependencyAnnotator.DEPENDENCIES, dependencyLayer(tokens, arcs));
  }

  /**
   * Verifies that a document lacking the entity layer is rejected the way every
   * annotator in the container rejects an absent required layer: {@code requires()}
   * names {@link Layers#ENTITIES}, so annotate throws the shared message naming the
   * missing layer instead of reading absence as an empty layer.
   */
  @Test
  void testAbsentEntityLayerIsRejectedWithTheLayerName() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("t", "<nsubj", null)));
    final IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class, () -> annotator.annotate(sleepingDocument()));
    Assertions.assertEquals("document lacks the required layer " + Layers.ENTITIES,
        e.getMessage());
  }

  /**
   * Verifies that a present but empty entity layer is valid input: no entities means no
   * pairs, so the annotator succeeds and adds an empty relations layer.
   */
  @Test
  void testEmptyEntityLayerProducesEmptyRelationsLayer() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("t", "<nsubj", null)));

    final Document annotated = annotator.annotate(
        sleepingDocument().with(Layers.ENTITIES, List.of()));

    Assertions.assertTrue(annotated.layers().contains(RelationAnnotator.RELATIONS));
    Assertions.assertTrue(annotated.get(RelationAnnotator.RELATIONS).isEmpty());
  }

  /**
   * Verifies the graceful degradation the annotator contract promises for documents
   * without content: every required layer is present but empty, so the annotator
   * succeeds and adds a present but empty relations layer instead of rejecting the
   * document.
   */
  @Test
  void testEmptyRequiredLayersDegradeToAnEmptyRelationsLayer() {
    final Document document = Document.of("")
        .with(Layers.TOKENS, List.of())
        .with(Layers.ENTITIES, List.of())
        .with(DependencyAnnotator.DEPENDENCIES, List.of());
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("t", "<nsubj", null)));

    final Document annotated = annotator.annotate(document);

    Assertions.assertTrue(annotated.layers().contains(RelationAnnotator.RELATIONS));
    Assertions.assertTrue(annotated.get(RelationAnnotator.RELATIONS).isEmpty());
  }

  /**
   * Verifies that registering the same pattern twice is accepted and applies the rule
   * twice: the annotator emits one relation per registered pattern, so the duplicate
   * produces two identical annotations.
   */
  @Test
  void testDuplicatePatternRegistrationEmitsDuplicateRelations() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", "acquired"),
        new RelationPattern("acquisition", "<nsubj >obj", "acquired")));

    final List<Annotation<RelationMention>> relations =
        annotator.annotate(acquisitionDocument()).get(RelationAnnotator.RELATIONS);

    Assertions.assertEquals(2, relations.size());
    Assertions.assertEquals(new RelationMention("acquisition", 0, 1),
        relations.get(0).value());
    Assertions.assertEquals(relations.get(0), relations.get(1));
  }

  /**
   * Verifies that a pattern collection containing {@code null} is rejected at
   * construction with the exact message.
   */
  @Test
  void testNullPatternInCollectionIsRejected() {
    final List<RelationPattern> patterns =
        Arrays.asList(new RelationPattern("t", "<nsubj", null), null);
    final IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class, () -> new RelationAnnotator(patterns));
    Assertions.assertEquals("patterns must not contain null", e.getMessage());
  }

  /**
   * Builds the parsed document "Istanbul, home of Bolt." whose first token is written
   * with the Turkish capital I with dot above (U+0130), with entities for that city
   * (index 0) and Bolt (index 1). The city is the sentence root, {@code home} is its
   * apposition, and {@code Bolt} is an {@code nmod} of {@code home}, so the path from the
   * city down to Bolt is {@code >appos >nmod} and the city itself is the pivot.
   *
   * @return A document with aligned token, entity, and dependency layers. Never
   *         {@code null}.
   */
  private static Document dottedCapitalPivotDocument() {
    final String text = DOTTED_CAPITAL_ISTANBUL + ", home of Bolt.";
    final List<Annotation<String>> tokens = List.of(
        new Annotation<>(new Span(0, 8), DOTTED_CAPITAL_ISTANBUL),
        new Annotation<>(new Span(8, 9), ","),
        new Annotation<>(new Span(10, 14), "home"),
        new Annotation<>(new Span(15, 17), "of"),
        new Annotation<>(new Span(18, 22), "Bolt"),
        new Annotation<>(new Span(22, 23), "."));
    final List<DependencyArc> arcs = List.of(
        new DependencyArc(DependencyArc.ROOT_HEAD, 0, "root"),
        new DependencyArc(2, 1, "punct"),
        new DependencyArc(0, 2, "appos"),
        new DependencyArc(4, 3, "case"),
        new DependencyArc(2, 4, "nmod"),
        new DependencyArc(0, 5, "punct"));
    return Document.of(text)
        .with(Layers.TOKENS, tokens)
        .with(Layers.ENTITIES, List.of(
            new Annotation<>(new Span(0, 8), "location"),
            new Annotation<>(new Span(18, 22), "organization")))
        .with(DependencyAnnotator.DEPENDENCIES, dependencyLayer(tokens, arcs));
  }

  /**
   * Verifies that the pivot form is lowercased with the project's case mapping: the pivot
   * token written with the Turkish capital I with dot above (U+0130) lowercases per code
   * point to {@code istanbul}, so a pattern carrying that trigger matches. The JDK's
   * locale-independent lowercasing instead expands U+0130 to an i followed by U+0307, a
   * form no constructible trigger can equal, which would silently drop the relation.
   */
  @Test
  void testPivotFormUsesTheProjectCaseMapping() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("located_in", ">appos >nmod", "istanbul")));

    final Document document = annotator.annotate(dottedCapitalPivotDocument());

    final List<Annotation<RelationMention>> relations =
        document.get(RelationAnnotator.RELATIONS);
    Assertions.assertEquals(1, relations.size());
    Assertions.assertEquals(new RelationMention("located_in", 0, 1),
        relations.get(0).value());
    Assertions.assertEquals(DOTTED_CAPITAL_ISTANBUL + ", home of Bolt", document.text().subSequence(
        relations.get(0).span().getStart(), relations.get(0).span().getEnd()).toString());
  }

  /**
   * The negative side of the mapping seam: the JDK's locale-independent lowercasing of
   * the dotted capital I expands to an {@code i} followed by the combining dot above
   * (U+0307), a spelling the project mapping never produces for the pivot, so a
   * trigger written that way matches nothing. This pins that the annotator compares
   * through the project mapping alone, and that a trigger prepared with
   * {@link String#toLowerCase()} does not silently work by accident.
   */
  @Test
  void testJdkLowercasedTriggerSpellingDoesNotMatchThePivot() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("located_in", ">appos >nmod", JDK_LOWERCASED_ISTANBUL)));

    final Document document = annotator.annotate(dottedCapitalPivotDocument());

    Assertions.assertTrue(document.get(RelationAnnotator.RELATIONS).isEmpty());
  }

  /**
   * Verifies that annotating a document that already carries a relations layer fails:
   * documents reject duplicate layers, so the second annotation pass throws.
   */
  @Test
  void testAnnotateRejectsAlreadyAnnotatedDocument() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", "acquired")));

    final Document annotated = annotator.annotate(acquisitionDocument());

    Assertions.assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(annotated));
  }
}
