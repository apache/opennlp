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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.depparse.DependencyAnnotator;
import opennlp.tools.depparse.DependencyArc;
import opennlp.tools.document.Annotation;
import opennlp.tools.document.Document;
import opennlp.tools.document.Layers;
import opennlp.tools.util.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests entity heads in token layers with reordered and empty spans. */
public class RelationEntityOverlapTest {

  private static final String TEXT = "Alice Smith hired Bob";
  private static final String TYPE = "employment";
  private static final String PERSON = "person";
  private static final String TEXT_ORDER = "0123";
  private static final String PREFIX = "\ud83d\udcda Note: ";
  private static final List<String> TOKEN_ORDERS = List.of(
      "0123", "0132", "0213", "0231", "0312", "0321",
      "1023", "1032", "1203", "1230", "1302", "1320",
      "2013", "2031", "2103", "2130", "2301", "2310",
      "3012", "3021", "3102", "3120", "3201", "3210");

  private final RelationAnnotator annotator = new RelationAnnotator(List.of(
      new RelationPattern(TYPE, "<nsubj >obj", "hired")));

  /** @return All token permutations, with text offsets and entity order variations. */
  static Stream<Arguments> tokenOrders() {
    return TOKEN_ORDERS.stream().flatMap(order -> Stream.of("", PREFIX).flatMap(prefix ->
        Stream.of(false, true).map(reverse -> Arguments.of(order, prefix, reverse))));
  }

  /** @return Empty entity positions for subject and object. */
  static Stream<Arguments> emptyEntities() {
    return IntStream.rangeClosed(0, TEXT.length()).boxed().flatMap(offset ->
        Stream.of(0, 1).map(entity -> Arguments.of(offset, entity)));
  }

  /** @return Empty token positions, including the beginning and end of the text. */
  static IntStream emptyTokens() {
    return IntStream.rangeClosed(0, TEXT.length());
  }

  /**
   * Reindexing a graph does not change the unique entity head or source-text ranges.
   *
   * @param order The token permutation.
   * @param prefix The text preceding the annotated words.
   * @param reverse Whether the entity layer lists Bob first.
   */
  @ParameterizedTest(name = "order={0}, prefix={1}, reverse={2}")
  @MethodSource("tokenOrders")
  void testTokenPermutations(String order, String prefix, boolean reverse) {
    final Document input = hiringDocument(order, prefix, reverse);
    final Document result = annotator.annotate(input);
    final var relations = result.get(RelationAnnotator.RELATIONS);
    assertEquals(List.of(new Annotation<>(new Span(prefix.length(), prefix.length() + TEXT.length()),
        new RelationMention(TYPE, reverse ? 1 : 0, reverse ? 0 : 1))), relations);
    assertEquals(TEXT, relations.get(0).span().getCoveredText(result.text()).toString());
    assertEquals("Alice Smith", result.get(Layers.ENTITIES).get(relations.get(0).value().subject())
        .span().getCoveredText(result.text()).toString());
    assertEquals("Bob", result.get(Layers.ENTITIES).get(relations.get(0).value().object())
        .span().getCoveredText(result.text()).toString());
    assertEquals(input.get(Layers.TOKENS), result.get(Layers.TOKENS));
    assertEquals(input.get(DependencyAnnotator.DEPENDENCIES), result.get(DependencyAnnotator.DEPENDENCIES));
    assertFalse(input.layers().contains(RelationAnnotator.RELATIONS));
  }

  /**
   * An empty entity shares no characters with any token.
   *
   * @param offset The empty span's offset.
   * @param entity The entity to replace.
   */
  @ParameterizedTest
  @MethodSource("emptyEntities")
  void testEmptyEntity(int offset, int entity) {
    final Document input = hiringDocument(TEXT_ORDER, "", false);
    final List<Annotation<String>> entities = new ArrayList<>(input.get(Layers.ENTITIES));
    entities.set(entity, new Annotation<>(new Span(offset, offset), PERSON));
    final Document changed = withLayers(input, input.get(Layers.TOKENS), entities,
        input.get(DependencyAnnotator.DEPENDENCIES));
    assertTrue(annotator.annotate(changed).get(RelationAnnotator.RELATIONS).isEmpty());
  }

  /**
   * An empty token cannot replace an entity's head, including at an interior offset.
   *
   * @param offset The empty token's offset.
   */
  @ParameterizedTest
  @MethodSource("emptyTokens")
  void testEmptyToken(int offset) {
    final Document input = hiringDocument(TEXT_ORDER, "", false);
    final List<Annotation<String>> tokens = new ArrayList<>();
    tokens.add(new Annotation<>(new Span(offset, offset), ""));
    tokens.addAll(input.get(Layers.TOKENS));
    final List<Annotation<DependencyArc>> arcs = new ArrayList<>();
    arcs.add(new Annotation<>(tokens.get(0).span(), new DependencyArc(3, 0, "trace")));
    for (final var annotation : input.get(DependencyAnnotator.DEPENDENCIES)) {
      final DependencyArc arc = annotation.value();
      final int head = arc.head() == DependencyArc.ROOT_HEAD ? arc.head() : arc.head() + 1;
      arcs.add(new Annotation<>(annotation.span(), new DependencyArc(head, arc.dependent() + 1,
          arc.relation())));
    }
    final Document changed = withLayers(input, tokens, input.get(Layers.ENTITIES), arcs);
    assertEquals(List.of(new Annotation<>(new Span(0, TEXT.length()), new RelationMention(TYPE, 0, 1))),
        annotator.annotate(changed).get(RelationAnnotator.RELATIONS));
  }

  /**
   * Partial intersections count, but spans limited to spaces do not select a token.
   *
   * @param start The subject span's start.
   * @param end The subject span's end.
   * @param expected The expected result count.
   */
  @ParameterizedTest
  @CsvSource({"0,11,1", "6,11,1", "7,10,1", "4,7,1", "5,12,1", "5,6,0", "11,12,0"})
  void testPartialEntitySpan(int start, int end, int expected) {
    final Document input = hiringDocument("0231", PREFIX, false);
    final List<Annotation<String>> entities = List.of(
        new Annotation<>(new Span(PREFIX.length() + start, PREFIX.length() + end), PERSON),
        input.get(Layers.ENTITIES).get(1));
    final Document changed = withLayers(input, input.get(Layers.TOKENS), entities,
        input.get(DependencyAnnotator.DEPENDENCIES));
    final var relations = annotator.annotate(changed).get(RelationAnnotator.RELATIONS);
    assertEquals(expected, relations.size());
    if (expected > 0) {
      assertEquals(new Span(PREFIX.length() + start, PREFIX.length() + TEXT.length()),
          relations.get(0).span());
      assertEquals(TEXT.substring(start), relations.get(0).span().getCoveredText(changed.text()).toString());
    }
  }

  /**
   * Builds the hiring example with remapped dependency indexes.
   *
   * @param order The token permutation.
   * @param prefix The text before the words.
   * @param reverse Whether the entity layer lists Bob first.
   * @return The document, without an output relation layer.
   */
  private Document hiringDocument(String order, String prefix, boolean reverse) {
    final List<Annotation<String>> original = List.of(
        new Annotation<>(new Span(0, 5), "Alice"),
        new Annotation<>(new Span(6, 11), "Smith"),
        new Annotation<>(new Span(12, 17), "hired"),
        new Annotation<>(new Span(18, 21), "Bob"));
    final int[] heads = {1, 2, DependencyArc.ROOT_HEAD, 2};
    final String[] labels = {"compound", "nsubj", "root", "obj"};
    final List<Annotation<String>> tokens = new ArrayList<>();
    final List<Annotation<DependencyArc>> arcs = new ArrayList<>();
    for (int index = 0; index < order.length(); index++) {
      final int originalIndex = order.charAt(index) - '0';
      final var token = original.get(originalIndex);
      final Span span = new Span(prefix.length() + token.span().getStart(),
          prefix.length() + token.span().getEnd());
      tokens.add(new Annotation<>(span, token.value()));
      final int originalHead = heads[originalIndex];
      final int head = originalHead == DependencyArc.ROOT_HEAD ? originalHead
          : order.indexOf('0' + originalHead);
      arcs.add(new Annotation<>(span, new DependencyArc(head, index, labels[originalIndex])));
    }
    final var subject = new Annotation<>(new Span(prefix.length(), prefix.length() + 11), PERSON);
    final var object = new Annotation<>(new Span(prefix.length() + 18, prefix.length() + 21), PERSON);
    return withLayers(Document.of(prefix + TEXT), tokens,
        reverse ? List.of(object, subject) : List.of(subject, object), arcs);
  }

  /**
   * Creates a document with the specified input layers over the original text.
   *
   * @param original The document providing the text.
   * @param tokens The token layer.
   * @param entities The entity layer.
   * @param arcs The dependency layer.
   * @return A document without output relations.
   */
  private Document withLayers(Document original, List<Annotation<String>> tokens,
      List<Annotation<String>> entities, List<Annotation<DependencyArc>> arcs) {
    return Document.of(original.text()).with(Layers.TOKENS, tokens)
        .with(Layers.ENTITIES, entities).with(DependencyAnnotator.DEPENDENCIES, arcs);
  }
}
