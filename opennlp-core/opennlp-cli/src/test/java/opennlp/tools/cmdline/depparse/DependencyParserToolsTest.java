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
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.depparse.DependencyGraph;
import opennlp.tools.depparse.DependencyModel;
import opennlp.tools.depparse.DependencyParserME;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.util.ObjectStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises CLI registration, CoNLL-U input, training, persistence and inference together. */
public class DependencyParserToolsTest {

  private static final String SENTENCE = """
      1\tdogs\tdog\tNOUN\tNNS\t_\t2\tnsubj\t_\t_
      2\trun\trun\tVERB\tVBP\t_\t0\troot\t_\t_

      """;

  @Test
  void testTrainEvaluateCrossValidateAndParse(@TempDir Path dir) throws Exception {
    Path data = dir.resolve("train.conllu");
    Path model = dir.resolve("parser.bin");
    Files.writeString(data, SENTENCE.repeat(20));
    assertTrue(CLI.getToolNames().contains("DependencyParserME"));
    assertTrue(CLI.getToolNames().contains("DependencyParserTrainerME"));
    assertTrue(CLI.getToolNames().contains("DependencyParserEvaluator"));
    assertTrue(CLI.getToolNames().contains("DependencyParserCrossValidator"));
    new DependencyParserTrainerTool().run("conllu", new String[] {
        "-lang", "eng", "-data", data.toString(), "-model", model.toString()});
    DependencyModel loaded = new DependencyModel(model);
    assertEquals(DependencyGraph.of(new int[] {1, -1}, new String[] {"nsubj", "root"}),
        new DependencyParserME(loaded).parse(new String[] {"dogs", "run"}, new String[] {"NOUN", "VERB"}));
    new DependencyParserEvaluatorTool().run("conllu", new String[] {
        "-model", model.toString(), "-data", data.toString()});
    new DependencyParserCrossValidatorTool().run("conllu", new String[] {
        "-lang", "eng", "-data", data.toString(), "-folds", "2"});
    InputStream previousIn = System.in;
    PrintStream previousOut = System.out;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      System.setIn(new ByteArrayInputStream("dogs_NOUN run_VERB\n".getBytes(StandardCharsets.UTF_8)));
      System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
      new DependencyParserMETool().run(new String[] {model.toString()});
    } finally {
      System.setIn(previousIn);
      System.setOut(previousOut);
    }
    assertEquals("1\tdogs\t_\t_\tNOUN\t_\t2\tnsubj\t_\t_\n"
        + "2\trun\t_\t_\tVERB\t_\t0\troot\t_\t_\n\n",
        output.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
  }

  @Test
  void testRegisteredStreamTagsetsAndInvalidOptions(@TempDir Path dir) throws Exception {
    Path data = dir.resolve("train.conllu");
    Files.writeString(data, SENTENCE);
    var factory = StreamFactoryRegistry.getFactory(DependencySample.class, "conllu");
    try (ObjectStream<DependencySample> samples = factory.create(new String[] {
        "-data", data.toString(), "-tagset", "x"})) {
      assertEquals("NNS", samples.read().getTags()[0]);
    }
    assertThrows(TerminateToolException.class, () -> factory.create(new String[] {
        "-data", data.toString(), "-encoding", "ISO-8859-1"}));
    assertThrows(TerminateToolException.class, () -> factory.create(new String[] {
        "-data", data.toString(), "-tagset", "invalid"}));
    assertThrows(TerminateToolException.class,
        () -> new DependencyParserCrossValidatorTool().run("conllu", new String[] {
            "-lang", "eng", "-data", data.toString(), "-folds", "1"}));
  }
}
