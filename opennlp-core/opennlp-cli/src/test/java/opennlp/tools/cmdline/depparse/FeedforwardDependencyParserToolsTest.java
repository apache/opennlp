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

package opennlp.tools.cmdline.depparse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.depparse.FeedforwardDependencyModel;
import opennlp.tools.depparse.FeedforwardDependencyParser;
import opennlp.tools.depparse.FeedforwardDependencyTrainer;
import opennlp.tools.ml.BeamSearch;
import opennlp.tools.util.model.BaseModel;

import static opennlp.tools.cmdline.depparse.DependencyToolTestSupport.EXPECTED_REVERSED;
import static opennlp.tools.cmdline.depparse.DependencyToolTestSupport.GOLD;
import static opennlp.tools.cmdline.depparse.DependencyToolTestSupport.PARSED_SENTENCE;
import static opennlp.tools.cmdline.depparse.DependencyToolTestSupport.PERFECT_SCORES;
import static opennlp.tools.cmdline.depparse.DependencyToolTestSupport.PERFECT_SCORES_EXCLUDING_PUNCTUATION;
import static opennlp.tools.cmdline.depparse.DependencyToolTestSupport.PREDICTED_SENTENCE;
import static opennlp.tools.cmdline.depparse.DependencyToolTestSupport.REPETITIONS;
import static opennlp.tools.cmdline.depparse.DependencyToolTestSupport.REVERSED_SENTENCE;
import static opennlp.tools.cmdline.depparse.DependencyToolTestSupport.SENTENCE;
import static opennlp.tools.cmdline.depparse.DependencyToolTestSupport.UTF_8_REQUIRED;
import static opennlp.tools.cmdline.depparse.DependencyToolTestSupport.logOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the feedforward dependency parser tools: registration, training, refinement, inference. */
public class FeedforwardDependencyParserToolsTest {

  /**
   * The training parameters of a small network that learns
   * {@link DependencyToolTestSupport#SENTENCE} quickly.
   */
  private static final String TRAINING_PARAMETERS = """
      EmbeddingSize=16
      HiddenSize=32
      Iterations=30
      BatchSize=32
      LearningRate=0.1
      L2Cost=0
      Dropout=0
      Cutoff=1
      """;

  /** The refinement parameters: one pass with a beam of three. */
  private static final String REFINE_PARAMETERS = """
      Iterations=1
      LearningRate=0.01
      BeamSize=3
      """;

  @TempDir
  private Path dir;

  private Path data;
  private Path model;
  private Path trainingParameters;
  private Path refineParameters;

  @BeforeEach
  void writeData() throws IOException {
    data = dir.resolve("train.conllu");
    model = dir.resolve("parser.bin");
    trainingParameters = dir.resolve("train.params");
    refineParameters = dir.resolve("refine.params");
    Files.writeString(data, SENTENCE.repeat(REPETITIONS));
    Files.writeString(trainingParameters, TRAINING_PARAMETERS);
    Files.writeString(refineParameters, REFINE_PARAMETERS);
  }

  /**
   * Runs the trainer tool with the small training parameters plus extra arguments.
   *
   * @param extra Arguments appended after the common ones.
   */
  private void train(String... extra) {
    final List<String> args = new ArrayList<>(List.of("-lang", "eng", "-data", data.toString(),
        "-model", model.toString(), "-params", trainingParameters.toString()));
    args.addAll(List.of(extra));
    new FeedforwardDependencyParserTrainerTool().run("conllu", args.toArray(new String[0]));
  }

  /**
   * Runs the parser tool on the given standard input.
   *
   * @param input The tagged sentences to parse.
   * @param args The tool arguments.
   * @return The standard output of the tool, with line breaks normalized to {@code \n}.
   */
  private static String parse(String input, String... args) {
    final InputStream previousIn = System.in;
    final PrintStream previousOut = System.out;
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
      System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
      new FeedforwardDependencyParserTool().run(args);
    } finally {
      System.setIn(previousIn);
      System.setOut(previousOut);
    }
    return output.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
  }

  /** The three feedforward tools are registered with the command line. */
  @Test
  void testToolsAreRegistered() {
    assertTrue(CLI.getToolNames().contains("FeedforwardDependencyParser"));
    assertTrue(CLI.getToolNames().contains("FeedforwardDependencyParserTrainer"));
    assertTrue(CLI.getToolNames().contains("FeedforwardDependencyParserEvaluator"));
  }

  /** No feedforward cross validator exists in the API, so none is registered. */
  @Test
  void testNoCrossValidatorIsRegistered() {
    assertFalse(CLI.getToolNames().contains("FeedforwardDependencyParserCrossValidator"));
  }

  /**
   * The trainer writes a model that loads, parses the training sentence, and records the
   * training parameters.
   */
  @Test
  void testTrainerWritesALoadableModel() throws IOException {
    train();
    final FeedforwardDependencyModel loaded = new FeedforwardDependencyModel(model);
    assertEquals("eng", loaded.getLanguage());
    assertEquals(GOLD, new FeedforwardDependencyParser(loaded)
        .parse(new String[] {"dogs", "run"}, new String[] {"NOUN", "VERB"}));
    assertEquals("30", loaded.getManifestProperty(BaseModel.TRAINING_ITERATIONS_PROPERTY));
    assertEquals("1", loaded.getManifestProperty(BaseModel.TRAINING_CUTOFF_PROPERTY));
    assertNull(loaded.getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER));
  }

  /**
   * Without {@code -params} the trainer's own defaults apply, not the maxent defaults
   * of {@code TrainingParameters.defaultParams()}.
   */
  @Test
  void testTrainerWithoutParamsUsesTheTrainerDefaults() throws IOException {
    new FeedforwardDependencyParserTrainerTool().run("conllu", new String[] {
        "-lang", "eng", "-data", data.toString(), "-model", model.toString()});
    final FeedforwardDependencyModel loaded = new FeedforwardDependencyModel(model);
    assertEquals(Integer.toString(FeedforwardDependencyTrainer.DEFAULT_EPOCHS),
        loaded.getManifestProperty(BaseModel.TRAINING_ITERATIONS_PROPERTY));
    assertEquals(Integer.toString(FeedforwardDependencyTrainer.DEFAULT_WORD_CUTOFF),
        loaded.getManifestProperty(BaseModel.TRAINING_CUTOFF_PROPERTY));
  }

  /** With {@code -refineParams} the trained model is refined and records the refinement beam size. */
  @Test
  void testTrainerRefinesWithTheRefineParameters() throws IOException {
    train("-refineParams", refineParameters.toString());
    final FeedforwardDependencyModel loaded = new FeedforwardDependencyModel(model);
    assertEquals("3", loaded.getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER));
    assertEquals("1", loaded.getManifestProperty(BaseModel.TRAINING_ITERATIONS_PROPERTY));
    assertEquals(GOLD, new FeedforwardDependencyParser(loaded, 3)
        .parse(new String[] {"dogs", "run"}, new String[] {"NOUN", "VERB"}));
  }

  /** With {@code -baseModel} the trainer refines a saved model instead of training one. */
  @Test
  void testTrainerRefinesAnExistingModel() throws IOException {
    train();
    final Path refined = dir.resolve("refined.bin");
    new FeedforwardDependencyParserTrainerTool().run("conllu", new String[] {
        "-lang", "eng", "-data", data.toString(), "-model", refined.toString(),
        "-baseModel", model.toString(), "-refineParams", refineParameters.toString()});
    final FeedforwardDependencyModel loaded = new FeedforwardDependencyModel(refined);
    assertEquals("3", loaded.getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER));
    assertEquals(GOLD, new FeedforwardDependencyParser(loaded, 3)
        .parse(new String[] {"dogs", "run"}, new String[] {"NOUN", "VERB"}));
  }

  /** {@code -baseModel} without {@code -refineParams} is rejected as an argument error. */
  @Test
  void testTrainerRejectsBaseModelWithoutRefineParams() {
    train();
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> new FeedforwardDependencyParserTrainerTool().run("conllu", new String[] {
            "-lang", "eng", "-data", data.toString(), "-model", dir.resolve("out.bin").toString(),
            "-baseModel", model.toString()}));
    assertEquals(1, exception.getCode());
    assertEquals("-baseModel requires -refineParams", exception.getMessage());
  }

  /** {@code -params} together with {@code -baseModel} is rejected, because no training runs. */
  @Test
  void testTrainerRejectsBaseModelWithParams() {
    train();
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> new FeedforwardDependencyParserTrainerTool().run("conllu", new String[] {
            "-lang", "eng", "-data", data.toString(), "-model", dir.resolve("out.bin").toString(),
            "-baseModel", model.toString(), "-refineParams", refineParameters.toString(),
            "-params", trainingParameters.toString()}));
    assertEquals(1, exception.getCode());
    assertEquals("-params does not apply with -baseModel; refinement reads -refineParams",
        exception.getMessage());
  }

  /** A {@code -lang} that differs from the language of the base model is rejected. */
  @Test
  void testTrainerRejectsBaseModelOfAnotherLanguage() {
    train();
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> new FeedforwardDependencyParserTrainerTool().run("conllu", new String[] {
            "-lang", "spa", "-data", data.toString(), "-model", dir.resolve("out.bin").toString(),
            "-baseModel", model.toString(), "-refineParams", refineParameters.toString()}));
    assertEquals(1, exception.getCode());
    assertEquals("-lang spa does not match the base model language eng", exception.getMessage());
  }

  /** Refinement needs a beam of at least two; the trainer's message ends the tool. */
  @Test
  void testTrainerRejectsRefinementBeamOfOne() throws IOException {
    Files.writeString(refineParameters, "BeamSize=1\n");
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> train("-refineParams", refineParameters.toString()));
    assertEquals(-1, exception.getCode());
    assertEquals("BeamSize must be at least 2: 1", exception.getMessage());
    assertFalse(Files.exists(model));
  }

  /** An out-of-range hyperparameter ends the tool with the trainer's message. */
  @Test
  void testTrainerRejectsInvalidHyperparameter() throws IOException {
    Files.writeString(trainingParameters, "HiddenSize=0\n");
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        this::train);
    assertEquals(-1, exception.getCode());
    assertEquals("HiddenSize must be positive", exception.getMessage());
    assertFalse(Files.exists(model));
  }

  /** Data without any trainable sample ends the tool with the trainer's message. */
  @Test
  void testTrainerOnEmptyDataTerminates() throws IOException {
    Files.writeString(data, "");
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        this::train);
    assertEquals(-1, exception.getCode());
    assertFalse(Files.exists(model));
  }

  /** The trainer passes an {@code -encoding} other than UTF-8 on to the format, which rejects it. */
  @Test
  void testTrainerRejectsOtherEncodings() {
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> train("-encoding", "ISO-8859-1"));
    assertEquals(-1, exception.getCode());
    assertEquals(UTF_8_REQUIRED, exception.getMessage());
  }

  /** The parser tool prints the greedy parse of a tagged line as CoNLL-U. */
  @Test
  void testParserToolPrintsConllu() {
    train();
    assertEquals(PARSED_SENTENCE, parse("dogs_NOUN run_VERB\n", model.toString()));
  }

  /** With {@code -beamSize} the parser tool parses every non-blank line and skips blank ones. */
  @Test
  void testParserToolParsesWithABeam() {
    train();
    assertEquals(PARSED_SENTENCE + PARSED_SENTENCE,
        parse("dogs_NOUN run_VERB\n\n  \ndogs_NOUN run_VERB\n", "-beamSize", "4", model.toString()));
  }

  /**
   * @return Argument lists the parser tool does not accept.
   */
  static Stream<Arguments> wrongParserArguments() {
    return Stream.of(
        Arguments.of((Object) new String[0]),
        Arguments.of((Object) new String[] {"-beamSize", "2"}),
        Arguments.of((Object) new String[] {"-beamSize", "2", "a", "b"}),
        Arguments.of((Object) new String[] {"-other", "2", "model"}),
        Arguments.of((Object) new String[] {"model", "-beamSize", "2"}));
  }

  /** Argument lists other than {@code model} or {@code -beamSize n model} end the tool with its help text. */
  @ParameterizedTest(name = "args = {0}")
  @MethodSource("wrongParserArguments")
  void testParserToolRejectsWrongArguments(String[] args) {
    final FeedforwardDependencyParserTool tool = new FeedforwardDependencyParserTool();
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> tool.run(args));
    assertEquals(1, exception.getCode());
    assertEquals(tool.getHelp(), exception.getMessage());
  }

  /** A {@code -beamSize} value that is not a decimal integer is rejected as an argument error. */
  @ParameterizedTest(name = "beamSize = \"{0}\"")
  @ValueSource(strings = {"abc", "2.5", "", " 2"})
  void testParserToolRejectsNonIntegerBeamSize(String beamSize) {
    train();
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> new FeedforwardDependencyParserTool().run(new String[] {
            "-beamSize", beamSize, model.toString()}));
    assertEquals(1, exception.getCode());
    assertEquals("-beamSize must be an integer: " + beamSize, exception.getMessage());
  }

  /** A {@code -beamSize} below one ends the tool with the parser's message. */
  @ParameterizedTest(name = "beamSize = {0}")
  @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
  void testParserToolRejectsBeamSizeBelowOne(int beamSize) {
    train();
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> new FeedforwardDependencyParserTool().run(new String[] {
            "-beamSize", Integer.toString(beamSize), model.toString()}));
    assertEquals(-1, exception.getCode());
    assertEquals("beamSize must be positive", exception.getMessage());
  }

  /** Without {@code -beamSize} the tools parse with the beam a refined model records. */
  @Test
  void testDefaultBeamSizeComesFromTheModel() throws IOException {
    train();
    assertEquals(1, FeedforwardDependencyParserTool.beamSize(
        new FeedforwardDependencyModel(model), null));
    train("-refineParams", refineParameters.toString());
    final FeedforwardDependencyModel refined = new FeedforwardDependencyModel(model);
    assertEquals(3, FeedforwardDependencyParserTool.beamSize(refined, null));
    assertEquals(5, FeedforwardDependencyParserTool.beamSize(refined, 5));
  }

  /** The evaluator logs perfect greedy scores on the training data. */
  @Test
  void testEvaluatorLogsAttachmentScores() {
    train();
    final List<String> log = logOf(FeedforwardDependencyParserEvaluatorTool.class,
        () -> new FeedforwardDependencyParserEvaluatorTool().run("conllu", new String[] {
            "-model", model.toString(), "-data", data.toString()}));
    assertEquals(List.of(PERFECT_SCORES, PERFECT_SCORES_EXCLUDING_PUNCTUATION), log);
  }

  /** The evaluator logs the same scores when it parses with a beam. */
  @Test
  void testEvaluatorLogsAttachmentScoresWithABeam() {
    train();
    final List<String> log = logOf(FeedforwardDependencyParserEvaluatorTool.class,
        () -> new FeedforwardDependencyParserEvaluatorTool().run("conllu", new String[] {
            "-model", model.toString(), "-data", data.toString(), "-beamSize", "4"}));
    assertEquals(List.of(PERFECT_SCORES, PERFECT_SCORES_EXCLUDING_PUNCTUATION), log);
  }

  /** A {@code -beamSize} below one ends the evaluator with the parser's message. */
  @Test
  void testEvaluatorRejectsBeamSizeBelowOne() {
    train();
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> new FeedforwardDependencyParserEvaluatorTool().run("conllu", new String[] {
            "-model", model.toString(), "-data", data.toString(), "-beamSize", "0"}));
    assertEquals(-1, exception.getCode());
    assertEquals("beamSize must be positive", exception.getMessage());
  }

  /** With {@code -misclassified true} the evaluator prints a misparsed sentence next to its gold tree. */
  @Test
  void testEvaluatorPrintsMisparsedSamples() throws IOException {
    train();
    final Path test = dir.resolve("test.conllu");
    Files.writeString(test, REVERSED_SENTENCE);
    final List<String> log = logOf(DependencyEvaluationErrorListener.class,
        () -> new FeedforwardDependencyParserEvaluatorTool().run("conllu", new String[] {
            "-model", model.toString(), "-data", test.toString(), "-misclassified", "true"}));
    assertEquals(List.of(EXPECTED_REVERSED + "\n" + PREDICTED_SENTENCE), log);
  }

  /** Without {@code -misclassified} the evaluator prints no samples. */
  @Test
  void testEvaluatorWithoutMisclassifiedFlagPrintsNoSamples() throws IOException {
    train();
    final Path test = dir.resolve("test.conllu");
    Files.writeString(test, REVERSED_SENTENCE);
    final List<String> log = logOf(DependencyEvaluationErrorListener.class,
        () -> new FeedforwardDependencyParserEvaluatorTool().run("conllu", new String[] {
            "-model", model.toString(), "-data", test.toString()}));
    assertEquals(List.of(), log);
  }

  /** The feedforward loader rejects a maxent dependency model as an invalid format. */
  @Test
  void testModelLoaderRejectsAMaxentModel() {
    new DependencyParserTrainerTool().run("conllu", new String[] {
        "-lang", "eng", "-data", data.toString(), "-model", model.toString()});
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> new FeedforwardDependencyModelLoader().load(model.toFile()));
    assertEquals(-1, exception.getCode());
    assertEquals("Model has invalid format", exception.getMessage());
  }

  /** The maxent loader rejects a feedforward model the same way, through the shared loader. */
  @Test
  void testMaxentModelLoaderRejectsAFeedforwardModel() {
    train();
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> new DependencyModelLoader().load(model.toFile()));
    assertEquals(-1, exception.getCode());
    assertEquals("Model has invalid format", exception.getMessage());
  }

  /** The feedforward loader loads a model the trainer wrote. */
  @Test
  void testModelLoaderLoadsAFeedforwardModel() {
    train();
    assertEquals("eng", new FeedforwardDependencyModelLoader().load(model.toFile()).getLanguage());
  }

  /** A {@code -params} file without {@code Iterations} trains for the trainer's default epochs. */
  @Test
  void testTrainerParamsWithoutIterationsUseTheTrainerDefault() throws IOException {
    Files.writeString(trainingParameters, "EmbeddingSize=16\nHiddenSize=32\nDropout=0\nCutoff=1\n");
    train();
    assertEquals(Integer.toString(FeedforwardDependencyTrainer.DEFAULT_EPOCHS),
        new FeedforwardDependencyModel(model).getManifestProperty(
            BaseModel.TRAINING_ITERATIONS_PROPERTY));
  }

  /** A missing {@code -refineParams} file ends the tool before training and writes no model. */
  @Test
  void testTrainerRejectsMissingRefineParamsFile() {
    final Path missing = dir.resolve("missing.params");
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> train("-refineParams", missing.toString()));
    assertEquals(-1, exception.getCode());
    assertEquals("The Training Parameter file does not exist! Path: " + missing.toAbsolutePath(),
        exception.getMessage());
    assertFalse(Files.exists(model));
  }

  /** A maxent model given as {@code -baseModel} ends the trainer through the shared loader. */
  @Test
  void testTrainerRejectsMaxentBaseModel() {
    new DependencyParserTrainerTool().run("conllu", new String[] {
        "-lang", "eng", "-data", data.toString(), "-model", model.toString()});
    final Path out = dir.resolve("out.bin");
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> new FeedforwardDependencyParserTrainerTool().run("conllu", new String[] {
            "-lang", "eng", "-data", data.toString(), "-model", out.toString(),
            "-baseModel", model.toString(), "-refineParams", refineParameters.toString()}));
    assertEquals(-1, exception.getCode());
    assertEquals("Model has invalid format", exception.getMessage());
    assertFalse(Files.exists(out));
  }

  /** Empty standard input produces no output. */
  @Test
  void testParserToolOnEmptyInputPrintsNothing() {
    train();
    assertEquals("", parse("", model.toString()));
  }

  /** A token without a tag separator ends the parser tool with the standard input error. */
  @Test
  void testParserToolRejectsTokenWithoutTag() {
    train();
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> parse("dogs run_VERB\n", model.toString()));
    assertEquals(-1, exception.getCode());
    assertEquals("IO Error while reading from stdin: Cannot find \"_\" inside token 'dogs'!",
        exception.getMessage());
  }

  /** The help text names {@code -beamSize} and its default, so a change to it is deliberate. */
  @Test
  void testParserToolHelp() {
    assertEquals("Usage: " + CLI.CMD + " FeedforwardDependencyParser [-beamSize n] model"
        + " < tagged-sentences\n"
        + "-beamSize n: keep n transition sequences per step. Defaults to the beam size the"
        + " model was refined with, or 1 (greedy) for a model that was not refined.",
        new FeedforwardDependencyParserTool().getHelp());
  }

  /** With a beam, {@code -misclassified true} prints the same misparsed sentence as greedy decoding. */
  @Test
  void testEvaluatorPrintsMisparsedSamplesWithABeam() throws IOException {
    train();
    final Path test = dir.resolve("test.conllu");
    Files.writeString(test, REVERSED_SENTENCE);
    final List<String> log = logOf(DependencyEvaluationErrorListener.class,
        () -> new FeedforwardDependencyParserEvaluatorTool().run("conllu", new String[] {
            "-model", model.toString(), "-data", test.toString(), "-misclassified", "true",
            "-beamSize", "4"}));
    assertEquals(List.of(EXPECTED_REVERSED + "\n" + PREDICTED_SENTENCE), log);
  }

  /** A {@code -beamSize} value that is not a decimal integer ends the evaluator with code 1. */
  @ParameterizedTest(name = "beamSize = \"{0}\"")
  @ValueSource(strings = {"abc", "2.5", "", " 2"})
  void testEvaluatorRejectsNonIntegerBeamSize(String beamSize) {
    train();
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> new FeedforwardDependencyParserEvaluatorTool().run("conllu", new String[] {
            "-model", model.toString(), "-data", data.toString(), "-beamSize", beamSize}));
    assertEquals(1, exception.getCode());
    assertTrue(exception.getMessage().startsWith("Invalid argument: -beamSize " + beamSize),
        exception.getMessage());
    assertTrue(exception.getMessage().endsWith("Value must be an integer!"),
        exception.getMessage());
  }

  /** Empty test data scores zero tokens without failing. */
  @Test
  void testEvaluatorOnEmptyDataLogsZeroScores() throws IOException {
    train();
    final Path empty = dir.resolve("empty.conllu");
    Files.writeString(empty, "");
    final List<String> log = logOf(FeedforwardDependencyParserEvaluatorTool.class,
        () -> new FeedforwardDependencyParserEvaluatorTool().run("conllu", new String[] {
            "-model", model.toString(), "-data", empty.toString()}));
    assertEquals(List.of("Tokens: 0; UAS: 0.0; LAS: 0.0",
        "Tokens excluding punctuation: 0; UAS: 0.0; LAS: 0.0"), log);
  }

  /** A model whose manifest records a beam size that is not an integer ends the parser tool. */
  @Test
  void testParserToolRejectsInvalidRecordedBeamSize() throws IOException {
    train("-refineParams", refineParameters.toString());
    final Path tampered = dir.resolve("tampered.bin");
    replaceInManifest(model, tampered, "BeamSize=3", "BeamSize=abc");
    final TerminateToolException exception = assertThrows(TerminateToolException.class,
        () -> parse("dogs_NOUN run_VERB\n", tampered.toString()));
    assertEquals(-1, exception.getCode());
    assertEquals("The model records an invalid BeamSize: abc", exception.getMessage());
  }

  /**
   * Copies a model package and replaces text in its manifest entry.
   *
   * @param source The model to copy.
   * @param target The file to write the changed model to.
   * @param from The manifest text to replace. Must occur in the manifest.
   * @param to The replacement text.
   * @throws IOException Thrown if reading or writing a model fails.
   */
  private static void replaceInManifest(Path source, Path target, String from, String to)
      throws IOException {
    try (ZipInputStream in = new ZipInputStream(Files.newInputStream(source));
         ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        byte[] content = in.readAllBytes();
        if ("manifest.properties".equals(entry.getName())) {
          final String manifest = new String(content, StandardCharsets.UTF_8);
          assertTrue(manifest.contains(from), manifest);
          content = manifest.replace(from, to).getBytes(StandardCharsets.UTF_8);
        }
        out.putNextEntry(new ZipEntry(entry.getName()));
        out.write(content);
        out.closeEntry();
      }
    }
  }
}
