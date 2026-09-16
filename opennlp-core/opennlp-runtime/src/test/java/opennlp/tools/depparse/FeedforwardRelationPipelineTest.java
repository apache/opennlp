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

package opennlp.tools.depparse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.relation.RelationAnnotator;
import opennlp.tools.relation.RelationMention;
import opennlp.tools.relation.RelationPattern;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Span;

import static opennlp.tools.depparse.DependencyTestSamples.corpus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/** Checks relation extraction after a serialized feedforward dependency parse. */
public class FeedforwardRelationPipelineTest {

  /** Text used by the prepared document. */
  private static final String TEXT = "the dog barks. she eats fish.";

  /** Training embedding width. */
  private static final int EMBEDDING_SIZE = 16;

  /** Training hidden-layer width. */
  private static final int HIDDEN_SIZE = 32;

  /** Training epoch count. */
  private static final int EPOCHS = 120;

  /** Training batch size. */
  private static final int BATCH_SIZE = 32;

  /** Training learning rate. */
  private static final double LEARNING_RATE = 0.05;

  /** Training word cutoff. */
  private static final int WORD_CUTOFF = 1;

  /** Training random seed. */
  private static final long SEED = 17L;

  /** Serialized trained model. */
  private static byte[] modelBytes;

  /**
   * Fits and serializes the shared model before parameter invocations.
   *
   * @throws IOException Thrown if sample input or serialization fails.
   */
  @BeforeAll
  static void trainModel() throws IOException {
    final FeedforwardDependencyTrainer.Settings settings =
        new FeedforwardDependencyTrainer.Settings(
            EMBEDDING_SIZE, HIDDEN_SIZE, EPOCHS, BATCH_SIZE, LEARNING_RATE,
            0.0, 0.0, WORD_CUTOFF, SEED);
    final FeedforwardDependencyModel model = FeedforwardDependencyTrainer.train(
        ObjectStreamUtils.createObjectStream(corpus()), settings);
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    model.serialize(output);
    modelBytes = output.toByteArray();
  }

  /**
   * Runs dependency and relation annotation with the selected decoder configuration.
   *
   * @param name The configuration name.
   * @param beamSize The parser beam size.
   * @throws IOException Thrown if model loading fails.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("parserConfigurations")
  void testFeedforwardDependencyToRelationPipeline(String name, int beamSize) throws IOException {
    final FeedforwardDependencyModel model = FeedforwardDependencyModel.load(
        new ByteArrayInputStream(modelBytes));
    final DependencyAnnotator dependencyAnnotator = new DependencyAnnotator(
        new FeedforwardDependencyParser(model, beamSize));
    final RelationAnnotator relationAnnotator = new RelationAnnotator(List.of(
        new RelationPattern("agent_of", "<nsubj", "barks"),
        new RelationPattern("eats", "<nsubj >obj", "eats")));

    assertPipeline(dependencyAnnotator, relationAnnotator);
    assertPipeline(dependencyAnnotator, relationAnnotator);
  }

  /**
   * Runs and checks one dependency-to-relation annotation pass.
   *
   * @param dependencyAnnotator The dependency annotator.
   * @param relationAnnotator The relation annotator.
   */
  private void assertPipeline(
      DependencyAnnotator dependencyAnnotator, RelationAnnotator relationAnnotator) {
    final Document input = preparedDocument();

    final Document parsed = dependencyAnnotator.annotate(input);
    final Document annotated = relationAnnotator.annotate(parsed);

    assertNotSame(input, parsed);
    assertNotSame(parsed, annotated);
    assertInputUnchanged(input);
    assertDependencies(parsed);
    assertRelations(annotated);
  }

  /**
   * Supplies decoder configurations.
   *
   * @return The named configurations.
   */
  private static Stream<Arguments> parserConfigurations() {
    return Stream.of(
        Arguments.of("greedy", 1),
        Arguments.of("beam", 4));
  }

  /**
   * Creates the prepared document layers.
   *
   * @return The input document.
   */
  private Document preparedDocument() {
    return Document.of(TEXT)
        .with(Layers.SENTENCES, List.of(
            new Annotation<>(new Span(0, 14), "the dog barks."),
            new Annotation<>(new Span(15, 29), "she eats fish.")))
        .with(Layers.TOKENS, List.of(
            new Annotation<>(new Span(0, 3), "the"),
            new Annotation<>(new Span(4, 7), "dog"),
            new Annotation<>(new Span(8, 13), "barks"),
            new Annotation<>(new Span(15, 18), "she"),
            new Annotation<>(new Span(19, 23), "eats"),
            new Annotation<>(new Span(24, 28), "fish")))
        .with(Layers.POS_TAGS, List.of(
            new Annotation<>(new Span(0, 3), "DT"),
            new Annotation<>(new Span(4, 7), "NN"),
            new Annotation<>(new Span(8, 13), "VBZ"),
            new Annotation<>(new Span(15, 18), "PRP"),
            new Annotation<>(new Span(19, 23), "VBZ"),
            new Annotation<>(new Span(24, 28), "NN")))
        .with(Layers.ENTITIES, List.of(
            new Annotation<>(new Span(4, 7), "animal"),
            new Annotation<>(new Span(8, 13), "action"),
            new Annotation<>(new Span(15, 18), "person"),
            new Annotation<>(new Span(24, 28), "food")));
  }

  /**
   * Checks that annotation did not modify the input document.
   *
   * @param input The prepared input document.
   */
  private void assertInputUnchanged(Document input) {
    assertEquals(TEXT, input.text().toString());
    assertEquals(Set.of(Layers.SENTENCES, Layers.TOKENS, Layers.POS_TAGS, Layers.ENTITIES),
        input.layers());
    assertEquals(6, input.get(Layers.TOKENS).size());
    assertEquals(4, input.get(Layers.ENTITIES).size());
    assertFalse(input.layers().contains(DependencyAnnotator.DEPENDENCIES));
    assertFalse(input.layers().contains(RelationAnnotator.RELATIONS));
  }

  /**
   * Checks arc values, token indexes, spans, and source text.
   *
   * @param parsed The dependency-annotated document.
   */
  private void assertDependencies(Document parsed) {
    final int[] expectedHeads = {1, 2, DependencyArc.ROOT_HEAD,
        4, DependencyArc.ROOT_HEAD, 4};
    final String[] expectedRelations = {"det", "nsubj", "root", "nsubj", "root", "obj"};
    final Span[] expectedSpans = {new Span(0, 3), new Span(4, 7), new Span(8, 13),
        new Span(15, 18), new Span(19, 23), new Span(24, 28)};
    final String[] expectedText = {"the", "dog", "barks", "she", "eats", "fish"};
    final List<Annotation<DependencyArc>> arcs = parsed.get(DependencyAnnotator.DEPENDENCIES);

    assertEquals(6, arcs.size());
    for (int i = 0; i < arcs.size(); i++) {
      final Annotation<DependencyArc> arc = arcs.get(i);
      assertEquals(expectedHeads[i], arc.value().head(), "head at " + i);
      assertEquals(i, arc.value().dependent(), "dependent at " + i);
      assertEquals(expectedRelations[i], arc.value().relation(), "relation at " + i);
      assertEquals(expectedSpans[i], arc.span(), "span at " + i);
      assertEquals(expectedText[i], arc.span().getCoveredText(parsed.text()).toString(),
          "source text at " + i);
    }
  }

  /**
   * Checks extracted relation values, spans, source text, and entity-group locality.
   *
   * @param annotated The relation-annotated document.
   */
  private void assertRelations(Document annotated) {
    final List<Annotation<RelationMention>> expected = List.of(
        new Annotation<>(new Span(4, 13), new RelationMention("agent_of", 0, 1)),
        new Annotation<>(new Span(15, 28), new RelationMention("eats", 2, 3)));
    final List<Annotation<RelationMention>> relations =
        annotated.get(RelationAnnotator.RELATIONS);

    assertEquals(expected, relations);
    assertEquals("dog barks", relations.get(0).span()
        .getCoveredText(annotated.text()).toString());
    assertEquals("she eats fish", relations.get(1).span()
        .getCoveredText(annotated.text()).toString());

    final int[] entitySentence = {0, 0, 1, 1};
    for (final Annotation<RelationMention> relation : relations) {
      assertEquals(entitySentence[relation.value().subject()],
          entitySentence[relation.value().object()]);
    }
  }
}
