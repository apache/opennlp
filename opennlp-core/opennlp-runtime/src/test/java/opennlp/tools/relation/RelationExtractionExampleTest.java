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

/**
 * Demonstrates the complete relation extraction flow as an application would run it: a
 * {@link Document} carrying token, entity, and dependency layers is passed through a
 * {@link RelationAnnotator} configured with two {@link RelationPattern} rules, and the
 * extracted relations are read back from {@link RelationAnnotator#RELATIONS}.
 *
 * <p>The token, entity, and dependency layers are constructed directly with the values a
 * tokenizer, a name finder, and a dependency parser would produce for the example
 * sentence, so the test is self-contained and every expected value is exact.</p>
 */
public class RelationExtractionExampleTest {

  /**
   * Builds the fully analyzed document "Acme Corp acquired Bolt in 2024." with the
   * layers a relation annotator consumes: tokens, entities, and dependency arcs.
   *
   * <p>The dependency analysis is the standard one for the sentence: {@code acquired} is
   * the root, {@code Corp} is its {@code nsubj} with {@code Acme} attached as
   * {@code compound}, {@code Bolt} is its {@code obj}, and {@code 2024} is its
   * {@code obl} with {@code in} attached as {@code case}. The entity layer holds
   * {@code Acme Corp} (index 0), {@code Bolt} (index 1), and {@code 2024} (index 2).</p>
   *
   * @return A document with aligned token, entity, and dependency layers. Never
   *         {@code null}.
   */
  private static Document analyzedDocument() {
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

  /**
   * Runs the annotator with two registered patterns and verifies the exact result: each
   * pattern fires exactly once, with the expected relation type, subject and object
   * entity indexes, argument direction, and covering span over the original text.
   */
  @Test
  void testTwoPatternsExtractTwoRelations() {
    final Document input = analyzedDocument();
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", "acquired"),
        new RelationPattern("acquisition_date", "<obj >obl", null)));

    final Document annotated = annotator.annotate(input);

    final List<Annotation<RelationMention>> relations =
        annotated.get(RelationAnnotator.RELATIONS);
    Assertions.assertEquals(2, relations.size());

    final Annotation<RelationMention> acquisition = relations.get(0);
    Assertions.assertEquals(new RelationMention("acquisition", 0, 1), acquisition.value());
    Assertions.assertEquals(new Span(0, 23), acquisition.span());
    Assertions.assertEquals("Acme Corp acquired Bolt", annotated.text().subSequence(
        acquisition.span().getStart(), acquisition.span().getEnd()).toString());

    final Annotation<RelationMention> acquisitionDate = relations.get(1);
    Assertions.assertEquals(new RelationMention("acquisition_date", 1, 2),
        acquisitionDate.value());
    Assertions.assertEquals(new Span(19, 31), acquisitionDate.span());
    Assertions.assertEquals("Bolt in 2024", annotated.text().subSequence(
        acquisitionDate.span().getStart(), acquisitionDate.span().getEnd()).toString());
  }

  /**
   * Verifies that annotation is non-destructive: the result carries the relations layer
   * while the input document stays without it, so callers can keep both versions.
   */
  @Test
  void testAnnotateAddsTheLayerWithoutTouchingTheInput() {
    final Document input = analyzedDocument();
    final RelationAnnotator annotator = new RelationAnnotator(List.of(
        new RelationPattern("acquisition", "<nsubj >obj", "acquired")));

    final Document annotated = annotator.annotate(input);

    Assertions.assertTrue(annotated.layers().contains(RelationAnnotator.RELATIONS));
    Assertions.assertFalse(input.layers().contains(RelationAnnotator.RELATIONS));
    Assertions.assertTrue(input.get(RelationAnnotator.RELATIONS).isEmpty());
    Assertions.assertEquals(1, annotated.get(RelationAnnotator.RELATIONS).size());
  }
}
