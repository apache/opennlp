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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.chunker.Chunker;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.parser.ParserTestUtil.CountingChunker;
import opennlp.tools.parser.ParserTestUtil.CountingTagger;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.TrainingParameters;

public class ParserFactoryTest {

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

  /**
   * Verifies that the model's own tagger and chunker, passed to the five-argument overload with
   * the default beam size and advance percentage, give the parses of the model-only factory method.
   *
   * @param model The model of the parser type under test.
   * @param expectedType The parser class of that type.
   */
  @ParameterizedTest(name = "{1}")
  @MethodSource("provideModels")
  void testCreateWithTheModelComponentsMatchesTheModelOnlyFactory(ParserModel model,
                                                                  Class<?> expectedType) {
    Parser parser = ParserFactory.create(model, new POSTaggerME(model.getParserTaggerModel()),
        new ChunkerME(model.getParserChunkerModel()), AbstractBottomUpParser.defaultBeamSize,
        AbstractBottomUpParser.defaultAdvancePercentage);
    Assertions.assertInstanceOf(expectedType, parser);

    Parse[] parses = parser.parse(ParserTestUtil.createTestSentence(), 2);
    Parse[] reference = ParserFactory.create(model).parse(ParserTestUtil.createTestSentence(), 2);
    ParserTestUtil.assertSameParses(reference, parses);
  }

  /**
   * Verifies that the five-argument overload parses with the supplied tagger and chunker and
   * never reads the model's tagger and chunker models.
   *
   * @param model The model of the parser type under test.
   * @param expectedType The parser class of that type.
   */
  @ParameterizedTest(name = "{1}")
  @MethodSource("provideModels")
  void testCreateUsesSuppliedTaggerAndChunker(ParserModel model, Class<?> expectedType) {
    CountingTagger tagger = new CountingTagger(new POSTaggerME(model.getParserTaggerModel()));
    CountingChunker chunker = new CountingChunker(new ChunkerME(model.getParserChunkerModel()));

    Parser parser = ParserFactory.create(ParserTestUtil.withInaccessibleComponentModels(model),
        tagger, chunker, AbstractBottomUpParser.defaultBeamSize,
        AbstractBottomUpParser.defaultAdvancePercentage);
    Assertions.assertInstanceOf(expectedType, parser);
    Parse parsed = parser.parse(ParserTestUtil.createTestSentence());

    Assertions.assertTrue(tagger.calls() > 0, "the supplied tagger was not used");
    Assertions.assertTrue(chunker.calls() > 0, "the supplied chunker was not used");
    Parse reference = ParserFactory.create(model).parse(ParserTestUtil.createTestSentence());
    Assertions.assertEquals(reference.toStringPennTreebank(), parsed.toStringPennTreebank());
  }

  /**
   * Verifies that the five-argument overload applies the beam size and advance percentage.
   *
   * @param model The model of the parser type under test.
   * @param expectedType The parser class of that type.
   */
  @ParameterizedTest(name = "{1}")
  @MethodSource("provideModels")
  void testCreateAppliesBeamSizeAndAdvancePercentage(ParserModel model, Class<?> expectedType) {
    Parser parser = ParserFactory.create(model, new POSTaggerME(model.getParserTaggerModel()),
        new ChunkerME(model.getParserChunkerModel()), 1, 1.0);
    Assertions.assertInstanceOf(expectedType, parser);

    Parse[] parses = parser.parse(ParserTestUtil.createTestSentence(), 3);
    Parse[] reference = ParserFactory.create(model, 1, 1.0).parse(ParserTestUtil.createTestSentence(), 3);
    Parse[] wide = ParserFactory.create(model).parse(ParserTestUtil.createTestSentence(), 3);
    ParserTestUtil.assertSameParses(reference, parses);
    Assertions.assertTrue(parses.length < wide.length, "a beam of one must prune parses");
  }

  /**
   * Verifies that the five-argument overload rejects a {@code null} model, tagger or chunker.
   *
   * @param nullArgument The argument passed as {@code null}.
   */
  @ParameterizedTest
  @ValueSource(strings = {"model", "tagger", "chunker"})
  void testCreateWithComponentsRejectsNullArgument(String nullArgument) {
    ParserModel model = "model".equals(nullArgument) ? null : chunkingModel;
    POSTagger tagger = "tagger".equals(nullArgument) ? null
        : new POSTaggerME(chunkingModel.getParserTaggerModel());
    Chunker chunker = "chunker".equals(nullArgument) ? null
        : new ChunkerME(chunkingModel.getParserChunkerModel());
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> ParserFactory.create(model, tagger, chunker, AbstractBottomUpParser.defaultBeamSize,
            AbstractBottomUpParser.defaultAdvancePercentage));
    Assertions.assertEquals(nullArgument + " must not be null", e.getMessage());
  }

  /**
   * Verifies that the five-argument overload rejects a beam size or an advance percentage
   * outside its range. The ranges themselves are tested in {@code ParamChecksTest}.
   *
   * @param beamSize The beam size to pass.
   * @param advancePercentage The advance percentage to pass.
   * @param expected The expected exception message.
   */
  @ParameterizedTest
  @CsvSource({
      "0, 0.95, beamSize must be positive",
      "20, 0.0, 'advancePercentage must be in (0, 1]'"
  })
  void testCreateWithComponentsRejectsOutOfRangeArgument(int beamSize, double advancePercentage,
                                                         String expected) {
    POSTagger tagger = new POSTaggerME(chunkingModel.getParserTaggerModel());
    Chunker chunker = new ChunkerME(chunkingModel.getParserChunkerModel());
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> ParserFactory.create(chunkingModel, tagger, chunker, beamSize, advancePercentage));
    Assertions.assertEquals(expected, e.getMessage());
  }

  /**
   * Verifies that {@link ParserFactory#create(ParserModel, int, double)} rejects a
   * {@code null} model, a beam size or an advance percentage outside its range.
   *
   * @param nullModel Whether the model is passed as {@code null}.
   * @param beamSize The beam size to pass.
   * @param advancePercentage The advance percentage to pass.
   * @param expected The expected exception message.
   */
  @ParameterizedTest
  @CsvSource({
      "true, 20, 0.95, model must not be null",
      "false, 0, 0.95, beamSize must be positive",
      "false, 20, 0.0, 'advancePercentage must be in (0, 1]'"
  })
  void testCreateRejectsInvalidArgument(boolean nullModel, int beamSize, double advancePercentage,
                                        String expected) {
    ParserModel model = nullModel ? null : chunkingModel;
    IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
        () -> ParserFactory.create(model, beamSize, advancePercentage));
    Assertions.assertEquals(expected, e.getMessage());
  }

  /**
   * Verifies that the five-argument overload accepts the boundaries of both ranges and returns a
   * parser that parses with them.
   *
   * @param beamSize The beam size to pass.
   * @param advancePercentage The advance percentage to pass.
   */
  @ParameterizedTest
  @CsvSource({"1, 1.0", "1, 0.01", "20, 1.0"})
  void testCreateWithComponentsAcceptsRangeBoundaries(int beamSize, double advancePercentage) {
    Parser parser = ParserFactory.create(chunkingModel,
        new POSTaggerME(chunkingModel.getParserTaggerModel()),
        new ChunkerME(chunkingModel.getParserChunkerModel()), beamSize, advancePercentage);
    Assertions.assertNotNull(parser.parse(ParserTestUtil.createTestSentence()));
  }
}
