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
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import opennlp.tools.depparse.DependencyAnnotator;
import opennlp.tools.depparse.DependencyArc;
import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;

public class RelationAnnotatorTest {

  /**
   * Builds the parsed document "Acme Corp acquired Bolt in 2024." with entities for
   * Acme Corp, Bolt, and 2024.
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
    final List<Annotation<DependencyArc>> dependencies = new ArrayList<>();
    for (final DependencyArc arc : arcs) {
      dependencies.add(new Annotation<>(tokens.get(arc.dependent()).span(), arc));
    }
    return Document.of(text)
        .with(Layers.TOKENS, tokens)
        .with(Layers.ENTITIES, List.of(
            new Annotation<>(new Span(0, 9), "organization"),
            new Annotation<>(new Span(19, 23), "organization"),
            new Annotation<>(new Span(27, 31), "date")))
        .with(DependencyAnnotator.DEPENDENCIES, dependencies);
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
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> new RelationMention("t", 1, 1));
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
  }
}
