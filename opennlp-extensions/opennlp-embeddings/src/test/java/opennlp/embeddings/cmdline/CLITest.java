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
package opennlp.embeddings.cmdline;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.TerminateToolException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command names the dispatcher offers and the help every tool produces. The names are the
 * module's public surface (TRAINING.md and the manual quote them), so a class rename that changes
 * a command has to fail here rather than in a user's shell.
 */
class CLITest {

  /** {@return the tools the dispatcher registers, as parameterized-test arguments} */
  static Stream<BasicCmdLineTool> tools() {
    return Stream.of(new AssembleModelTool(), new DistillModelTool(), new QuantizeModelTool(),
        new NormalizeDictionaryTool(), new NormalizeReporterTool(), new LearnVocabularyTool(),
        new EvalVectorSearchTool());
  }

  @Test
  void testOffersExactlyTheModelCommands() {
    assertEquals(Set.of("AssembleModel", "DistillModel", "QuantizeModel",
        "NormalizeDictionary", "NormalizeReporter", "LearnVocabulary", "EvalVectorSearch"),
        CLI.getToolNames());
  }

  @Test
  void testTheToolNamesCannotBeModifiedByACaller() {
    final Set<String> names = CLI.getToolNames();

    assertThrows(UnsupportedOperationException.class, () -> names.add("Other"));
  }

  @ParameterizedTest
  @MethodSource("tools")
  void testEveryRegisteredToolDescribesItself(BasicCmdLineTool tool) {
    assertTrue(CLI.getToolNames().contains(tool.getName()),
        tool.getName() + " must be registered with the dispatcher");
    assertFalse(tool.getShortDescription().isBlank(),
        tool.getName() + " must have a short description for the usage listing");
    assertTrue(tool.getHelp().contains(tool.getName()), tool.getHelp());
  }

  @Test
  void testDistillHelpNamesEveryParameter() {
    final String help = new DistillModelTool().getHelp();

    assertTrue(help.contains("-teacher hf-id-or-path"), help);
    assertTrue(help.contains("-out dir"), help);
    // The optional parameters are bracketed, so a user can see they may be omitted.
    assertTrue(help.contains("[-pcaDims "), help);
    assertTrue(help.contains("[-terms "), help);
  }

  @Test
  void testAssembleHelpNamesItsParameter() {
    final String help = new AssembleModelTool().getHelp();

    assertTrue(help.contains("-modelDir dir"), help);
  }

  @Test
  void testEvalVectorSearchHelpNamesEveryParameter() {
    final String help = new EvalVectorSearchTool().getHelp();

    assertTrue(help.contains("-model dir"), help);
    assertTrue(help.contains("-passages file"), help);
    assertTrue(help.contains("-dictionary file"), help);
    assertTrue(help.contains("-out file"), help);
    assertTrue(help.contains("[-bits num]"), help);
    assertTrue(help.contains("[-seed num]"), help);
    assertTrue(help.contains("[-topK num]"), help);
  }

  @Test
  void testCorpusToolHelpNamesEveryParameter() {
    final String dictionaryHelp = new NormalizeDictionaryTool().getHelp();
    final String reporterHelp = new NormalizeReporterTool().getHelp();
    final String vocabularyHelp = new LearnVocabularyTool().getHelp();

    assertTrue(dictionaryHelp.contains("-rawDir dir"), dictionaryHelp);
    assertTrue(dictionaryHelp.contains("-out file"), dictionaryHelp);
    assertTrue(reporterHelp.contains("-rawDir dir"), reporterHelp);
    assertTrue(reporterHelp.contains("-out file"), reporterHelp);
    assertTrue(vocabularyHelp.contains("-dictionary file"), vocabularyHelp);
    assertTrue(vocabularyHelp.contains("-passages file"), vocabularyHelp);
    assertTrue(vocabularyHelp.contains("-out file"), vocabularyHelp);
    assertTrue(vocabularyHelp.contains("[-minFrequency n]"), vocabularyHelp);
    assertTrue(vocabularyHelp.contains("[-maxTerms n]"), vocabularyHelp);
  }

  @Test
  void testQuantizeHelpNamesEveryParameter() {
    final String help = new QuantizeModelTool().getHelp();

    assertTrue(help.contains("-modelDir dir"), help);
    assertTrue(help.contains("[-bits bits]"), help);
    assertTrue(help.contains("[-seed seed]"), help);
  }

  @Test
  void testQuantizeReportsInvalidModelContentAsAUserError(@TempDir Path directory) {
    final TerminateToolException error = assertThrows(TerminateToolException.class,
        () -> new QuantizeModelTool().run(new String[] {"-modelDir", directory.toString()}));

    assertEquals(1, error.getCode());
  }
}
