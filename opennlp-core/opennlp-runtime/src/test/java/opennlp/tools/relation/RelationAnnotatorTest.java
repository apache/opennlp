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
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.depparse.DependencyAnnotator;
import opennlp.tools.depparse.DependencyArc;
import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;

/** Tests relation matching, dependency paths, and invalid input layers. */
public class RelationAnnotatorTest {

  private static final String DOTTED_CAPITAL_ISTANBUL = "\u0130stanbul";

  private static final String JDK_LOWERCASED_ISTANBUL = "i\u0307stanbul";

  private static final String NO_BREAK_SPACE = "\u00A0";

  private static List<Annotation<DependencyArc>> dependencyLayer(
      List<Annotation<String>> tokens, List<DependencyArc> arcs) {
    final List<Annotation<DependencyArc>> dependencies = new ArrayList<>(arcs.size());
    for (final DependencyArc arc : arcs) {
      dependencies.add(new Annotation<>(tokens.get(arc.dependent()).span(), arc));
    }
    return dependencies;
  }

  private static Document twoEntityDocument(List<DependencyArc> arcs) {
    final List<Annotation<String>> tokens = List.of(
        new Annotation<>(new Span(0, 1), "A"),
        new Annotation<>(new Span(2, 3), "B"));
    final List<Annotation<DependencyArc>> dependencies = new ArrayList<>();
    for (int i = 0; i < arcs.size(); i++) {
      dependencies.add(new Annotation<>(tokens.get(i).span(), arcs.get(i)));
    }
    return Document.of("A B")
        .with(Layers.TOKENS, tokens)
        .with(Layers.ENTITIES, tokens)
        .with(DependencyAnnotator.DEPENDENCIES, dependencies);
  }

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

  @Test
  void testTriggerMismatchDoesNotMatch() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", "bought")));

    Assertions.assertTrue(annotator.annotate(acquisitionDocument())
        .get(RelationAnnotator.RELATIONS).isEmpty());
  }

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

  @Test
  void testMultiwordEntityHeadIsTheOutwardToken() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", "acquired")));

    final List<Annotation<RelationMention>> relations =
        annotator.annotate(acquisitionDocument()).get(RelationAnnotator.RELATIONS);

    Assertions.assertEquals(1, relations.size());
    Assertions.assertEquals(0, relations.get(0).value().subject());
  }

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
    Assertions.assertEquals(Set.of(Layers.TOKENS, Layers.ENTITIES,
        DependencyAnnotator.DEPENDENCIES), annotator.requires());
    Assertions.assertEquals(Set.of(RelationAnnotator.RELATIONS), annotator.provides());
    Assertions.assertEquals("RelationAnnotator", annotator.toString());
  }

  @Test
  void testInvalidDependencyLayersAreRejected() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("t", "<dep", null)));

    final Document missingArc = twoEntityDocument(List.of(
        new DependencyArc(DependencyArc.ROOT_HEAD, 0, "root")));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(missingArc));

    final Document duplicateDependent = twoEntityDocument(List.of(
        new DependencyArc(1, 0, "dep"),
        new DependencyArc(DependencyArc.ROOT_HEAD, 0, "root")));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(duplicateDependent));

    final Document headOutOfRange = twoEntityDocument(List.of(
        new DependencyArc(2, 0, "dep"),
        new DependencyArc(DependencyArc.ROOT_HEAD, 1, "root")));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(headOutOfRange));
  }

  @Test
  void testCyclicDependencyLayerIsRejected() {
    final Document cycle = twoEntityDocument(List.of(
        new DependencyArc(1, 0, "dep"),
        new DependencyArc(0, 1, "dep")));
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("t", "<dep >dep", null)));

    final IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class, () -> annotator.annotate(cycle));
    Assertions.assertEquals("dependency layer contains a cycle at token 0",
        e.getMessage());
  }

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

  @Test
  void testPathLongerThanPatternDoesNotMatch() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("t", "<nsubj", null)));

    Assertions.assertTrue(annotator.annotate(acquisitionDocument())
        .get(RelationAnnotator.RELATIONS).isEmpty());
  }

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

  @Test
  void testAbsentEntityLayerIsRejectedWithTheLayerName() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("t", "<nsubj", null)));
    final IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class, () -> annotator.annotate(sleepingDocument()));
    Assertions.assertEquals("document lacks the required layer " + Layers.ENTITIES,
        e.getMessage());
  }

  @Test
  void testEmptyEntityLayerProducesEmptyRelationsLayer() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("t", "<nsubj", null)));

    final Document annotated = annotator.annotate(
        sleepingDocument().with(Layers.ENTITIES, List.of()));

    Assertions.assertTrue(annotated.layers().contains(RelationAnnotator.RELATIONS));
    Assertions.assertTrue(annotated.get(RelationAnnotator.RELATIONS).isEmpty());
  }

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

  @Test
  void testNullPatternInCollectionIsRejected() {
    final List<RelationPattern> patterns =
        Arrays.asList(new RelationPattern("t", "<nsubj", null), null);
    final IllegalArgumentException e = Assertions.assertThrows(
        IllegalArgumentException.class, () -> new RelationAnnotator(patterns));
    Assertions.assertEquals("patterns must not contain null", e.getMessage());
  }

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

  @Test
  void testPathCanEndAtAnAncestorEntity() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("located_in", "<nmod <appos", "istanbul")));

    final List<Annotation<RelationMention>> relations =
        annotator.annotate(dottedCapitalPivotDocument()).get(RelationAnnotator.RELATIONS);

    Assertions.assertEquals(List.of(new Annotation<>(new Span(0, 22),
        new RelationMention("located_in", 1, 0))), relations);
  }

  @Test
  void testJdkLowercasedTriggerSpellingDoesNotMatchThePivot() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("located_in", ">appos >nmod", JDK_LOWERCASED_ISTANBUL)));

    final Document document = annotator.annotate(dottedCapitalPivotDocument());

    Assertions.assertTrue(document.get(RelationAnnotator.RELATIONS).isEmpty());
  }

  @Test
  void testAnnotateRejectsAlreadyAnnotatedDocument() {
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", "acquired")));

    final Document annotated = annotator.annotate(acquisitionDocument());

    Assertions.assertThrows(IllegalArgumentException.class,
        () -> annotator.annotate(annotated));
  }

  @Test
  void testAnnotatorCanBeSharedAcrossThreads() {
    final Document input = acquisitionDocument();
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", "acquired")));

    IntStream.range(0, 100).parallel().forEach(i -> {
      final List<Annotation<RelationMention>> relations =
          annotator.annotate(input).get(RelationAnnotator.RELATIONS);
      Assertions.assertEquals(List.of(new Annotation<>(new Span(0, 23),
          new RelationMention("acquisition", 0, 1))), relations);
    });
  }
}
