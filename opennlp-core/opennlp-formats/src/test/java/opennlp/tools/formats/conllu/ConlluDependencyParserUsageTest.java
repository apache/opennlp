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

package opennlp.tools.formats.conllu;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.depparse.DependencyArc;
import opennlp.tools.depparse.DependencyEvaluator;
import opennlp.tools.depparse.DependencyGraph;
import opennlp.tools.depparse.DependencyModel;
import opennlp.tools.depparse.DependencyParserME;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Demonstrates the full dependency parsing workflow on a self-contained fixture: read
 * gold sentences from CoNLL-U content, train a {@link DependencyParserME}, parse a
 * sentence, inspect the resulting {@link DependencyGraph}, and persist the model.
 *
 * <p>The fixture holds three tiny sentences inline, so the test needs no external data.
 * A real treebank provides thousands of sentences; here each fixture sentence is
 * repeated to give the trainer the same evidence many times, which lets the model
 * memorize the fixture and makes every expected value exact.</p>
 */
public class ConlluDependencyParserUsageTest {

  /**
   * Joins the ten CoNLL-U columns of one word line with tabs.
   *
   * @param fields The column values; exactly ten are expected by the format.
   * @return The joined word line. Never {@code null}.
   */
  private static String line(String... fields) {
    return String.join("\t", fields);
  }

  /**
   * The training fixture: three gold sentences in CoNLL-U form. The {@code HEAD} column
   * is one-based with {@code 0} marking the root; the reader converts it to the
   * zero-based convention of {@link DependencyGraph}.
   */
  private static final String CONLLU = String.join("\n",
      "# text = the dog barks",
      line("1", "the", "the", "DET", "DT", "_", "2", "det", "_", "_"),
      line("2", "dog", "dog", "NOUN", "NN", "_", "3", "nsubj", "_", "_"),
      line("3", "barks", "bark", "VERB", "VBZ", "_", "0", "root", "_", "_"),
      "",
      "# text = dogs bark",
      line("1", "dogs", "dog", "NOUN", "NNS", "_", "2", "nsubj", "_", "_"),
      line("2", "bark", "bark", "VERB", "VBP", "_", "0", "root", "_", "_"),
      "",
      "# text = she eats fish",
      line("1", "she", "she", "PRON", "PRP", "_", "2", "nsubj", "_", "_"),
      line("2", "eats", "eat", "VERB", "VBZ", "_", "0", "root", "_", "_"),
      line("3", "fish", "fish", "NOUN", "NN", "_", "2", "obj", "_", "_"),
      "") + "\n";

  private static DependencyModel model;
  private static DependencyParserME parser;

  /**
   * Reads the fixture sentences through the CoNLL-U reader.
   *
   * @return One sample per fixture sentence, in file order. Never {@code null}.
   * @throws IOException Thrown if reading the in-memory content fails.
   */
  private static List<DependencySample> readFixture() throws IOException {
    final InputStreamFactory in =
        () -> new ByteArrayInputStream(CONLLU.getBytes(StandardCharsets.UTF_8));
    final List<DependencySample> samples = new ArrayList<>();
    try (ConlluDependencySampleStream stream =
        new ConlluDependencySampleStream(in, ConlluTagset.U)) {
      DependencySample sample;
      while ((sample = stream.read()) != null) {
        samples.add(sample);
      }
    }
    return samples;
  }

  /**
   * Trains the parser once for all tests: read the fixture, repeat it for evidence,
   * and hand the samples to the trainer.
   *
   * @throws IOException Thrown if reading the in-memory samples fails.
   */
  @BeforeAll
  static void trainParser() throws IOException {
    final List<DependencySample> fixture = readFixture();
    final List<DependencySample> trainingSamples = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      trainingSamples.addAll(fixture);
    }
    final TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, 0);
    model = DependencyParserME.train("eng",
        ObjectStreamUtils.createObjectStream(trainingSamples), parameters);
    parser = new DependencyParserME(model);
  }

  @Test
  void testReaderDeliversTheGoldAnnotation() throws IOException {
    final List<DependencySample> fixture = readFixture();
    assertEquals(3, fixture.size());
    final DependencySample first = fixture.get(0);
    assertEquals(DependencyGraph.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"}), first.getGraph());
    assertEquals("NOUN", first.getTags()[1]);
  }

  @Test
  void testParseAssignsHeadsAndRelations() {
    // Parsing takes the tokens and their part-of-speech tags; the result names, for
    // every token, its head token and the relation between the two.
    final DependencyGraph parse = parser.parse(
        new String[] {"the", "dog", "barks"}, new String[] {"DET", "NOUN", "VERB"});
    assertEquals(1, parse.headOf(0));
    assertEquals("det", parse.relationOf(0));
    assertEquals(2, parse.headOf(1));
    assertEquals("nsubj", parse.relationOf(1));
    assertEquals(DependencyArc.ROOT_HEAD, parse.headOf(2));
    assertEquals("root", parse.relationOf(2));
    assertEquals(2, parse.root());
  }

  @Test
  void testEvaluatorScoresTheParserAgainstGoldSamples() throws IOException {
    // The evaluator parses each gold sentence and accumulates the two standard scores;
    // on its own training fixture the memorizing model is exact on all eight tokens.
    final DependencyEvaluator evaluator = new DependencyEvaluator(parser);
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(readFixture()));
    assertEquals(1.0d, evaluator.getUas());
    assertEquals(1.0d, evaluator.getLas());
    assertEquals(8, evaluator.getWordCount());
  }

  @Test
  void testPersistedModelParsesLikeTheOriginal(@TempDir Path dir) throws IOException {
    // A trained model is saved to a file and loaded back like any other tool model; the
    // reloaded parser must produce the exact same parse as the original.
    final Path file = dir.resolve("en-depparse.bin");
    model.serialize(file);
    final DependencyParserME reloaded = new DependencyParserME(new DependencyModel(file));
    assertEquals(DependencyGraph.of(new int[] {1, -1, 1},
            new String[] {"nsubj", "root", "obj"}),
        reloaded.parse(new String[] {"she", "eats", "fish"},
            new String[] {"PRON", "VERB", "NOUN"}));
  }
}
