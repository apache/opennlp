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


package opennlp.tools.parser;

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.parser.ParserTestUtil.CountingChunker;
import opennlp.tools.parser.ParserTestUtil.CountingTagger;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.TrainingParameters;

public class ParserBuilderTest {

  /* Trained dynamically before test */
  private static ParserModel chunkingModel;
  private static ParserModel treeInsertModel;

  @BeforeAll
  public static void setupEnvironment() throws IOException {
    HeadRules headRules = ParserTestUtil.createTestHeadRules();
    chunkingModel = opennlp.tools.parser.chunking.Parser.train("eng",
        ParserTestUtil.openTestTrainingData(), headRules, TrainingParameters.defaultParams());
    treeInsertModel = opennlp.tools.parser.treeinsert.Parser.train("eng",
        ParserTestUtil.openTestTrainingData(), headRules, TrainingParameters.defaultParams());
    Assertions.assertNotNull(chunkingModel);
    Assertions.assertNotNull(treeInsertModel);
  }

  private static Stream<Arguments> provideModels() {
    return Stream.of(
        Arguments.of(chunkingModel, opennlp.tools.parser.chunking.Parser.class),
        Arguments.of(treeInsertModel, opennlp.tools.parser.treeinsert.Parser.class));
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("provideModels")
  void testBuildWithDefaultsMatchesFactory(ParserModel model, Class<?> expectedType) {
    Parser parser = ParserFactory.builder(model).build();
    Assertions.assertInstanceOf(expectedType, parser);

    Parse[] parses = parser.parse(ParserTestUtil.createTestSentence(), 2);
    Parse[] reference = ParserFactory.create(model).parse(ParserTestUtil.createTestSentence(), 2);
    ParserTestUtil.assertSameParses(reference, parses);
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("provideModels")
  void testBuildUsesSuppliedTaggerAndChunker(ParserModel model, Class<?> expectedType) {
    CountingTagger tagger = new CountingTagger(new POSTaggerME(model.getParserTaggerModel()));
    CountingChunker chunker = new CountingChunker(new ChunkerME(model.getParserChunkerModel()));

    Parser parser = ParserFactory.builder(ParserTestUtil.withInaccessibleComponentModels(model))
        .tagger(tagger)
        .chunker(chunker)
        .build();
    Assertions.assertInstanceOf(expectedType, parser);
    Parse parsed = parser.parse(ParserTestUtil.createTestSentence());

    Assertions.assertTrue(tagger.calls() > 0, "the supplied tagger was not used");
    Assertions.assertTrue(chunker.calls() > 0, "the supplied chunker was not used");
    Parse reference = ParserFactory.create(model).parse(ParserTestUtil.createTestSentence());
    Assertions.assertEquals(reference.toStringPennTreebank(), parsed.toStringPennTreebank());
  }

  @ParameterizedTest(name = "{1}")
  @MethodSource("provideModels")
  void testBuildAppliesBeamSizeAndAdvancePercentage(ParserModel model, Class<?> expectedType) {
    Parser parser = ParserFactory.builder(model).beamSize(1).advancePercentage(1.0).build();
    Assertions.assertInstanceOf(expectedType, parser);

    Parse[] parses = parser.parse(ParserTestUtil.createTestSentence(), 3);
    Parse[] reference = ParserFactory.create(model, 1, 1.0).parse(ParserTestUtil.createTestSentence(), 3);
    Parse[] wide = ParserFactory.create(model).parse(ParserTestUtil.createTestSentence(), 3);
    Assertions.assertEquals(reference.length, parses.length);
    Assertions.assertTrue(parses.length < wide.length, "a beam of one must prune parses");
  }

  @Test
  void testBuilderRejectsNullModel() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> ParserFactory.builder(null));
  }

  @Test
  void testBuilderRejectsNullTagger() {
    ParserBuilder builder = ParserFactory.builder(chunkingModel);
    Assertions.assertThrows(IllegalArgumentException.class, () -> builder.tagger(null));
  }

  @Test
  void testBuilderRejectsNullChunker() {
    ParserBuilder builder = ParserFactory.builder(chunkingModel);
    Assertions.assertThrows(IllegalArgumentException.class, () -> builder.chunker(null));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void testBuilderRejectsBeamSize(int beamSize) {
    ParserBuilder builder = ParserFactory.builder(chunkingModel);
    Assertions.assertThrows(IllegalArgumentException.class, () -> builder.beamSize(beamSize));
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, -0.1, 1.01, Double.NaN})
  void testBuilderRejectsAdvancePercentage(double advancePercentage) {
    ParserBuilder builder = ParserFactory.builder(chunkingModel);
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> builder.advancePercentage(advancePercentage));
  }
}
