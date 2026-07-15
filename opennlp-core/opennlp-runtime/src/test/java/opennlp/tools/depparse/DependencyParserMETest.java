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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.util.ObjectStreamUtils;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link DependencyParserME} end to end: training on a tiny corpus must let the
 * greedy parser reproduce the training sentences, which proves the oracle, event stream,
 * feature generation, and decode loop agree with each other.
 */
public class DependencyParserMETest {

  private static DependencyModel model;
  private static DependencyParserME parser;

  private static DependencySample sample(String[] tokens, String[] tags, int[] heads,
      String[] relations) {
    return new DependencySample(tokens, tags, DependencyGraph.of(heads, relations));
  }

  private static List<DependencySample> corpus() {
    final List<DependencySample> distinct = List.of(
        sample(new String[] {"the", "dog", "barks"}, new String[] {"DT", "NN", "VBZ"},
            new int[] {1, 2, -1}, new String[] {"det", "nsubj", "root"}),
        sample(new String[] {"dogs", "bark"}, new String[] {"NNS", "VBP"},
            new int[] {1, -1}, new String[] {"nsubj", "root"}),
        sample(new String[] {"she", "eats", "fish"}, new String[] {"PRP", "VBZ", "NN"},
            new int[] {1, -1, 1}, new String[] {"nsubj", "root", "obj"}));
    final List<DependencySample> corpus = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      corpus.addAll(distinct);
    }
    return corpus;
  }

  @BeforeAll
  static void trainParser() throws IOException {
    final TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, 0);
    model = DependencyParserME.train("eng",
        ObjectStreamUtils.createObjectStream(corpus()), parameters);
    parser = new DependencyParserME(model);
  }

  @Test
  void testMemorizesTrainingSentences() {
    final DependencyGraph parsed = parser.parse(new String[] {"the", "dog", "barks"},
        new String[] {"DT", "NN", "VBZ"});
    assertEquals(DependencyGraph.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"}), parsed);
  }

  @Test
  void testParseAlwaysYieldsASingleRootedTree() {
    // an unseen sentence must still decode to a valid graph, whatever its quality
    final DependencyGraph parsed = parser.parse(new String[] {"cats", "sleep"},
        new String[] {"NNS", "VBP"});
    assertEquals(2, parsed.size());
    parsed.root();
  }

  @Test
  void testEvaluatorScoresPerfectlyOnTrainingData() throws IOException {
    final DependencyEvaluator evaluator = new DependencyEvaluator(parser);
    evaluator.evaluate(ObjectStreamUtils.createObjectStream(corpus()));
    assertEquals(1.0d, evaluator.getUas());
    assertEquals(1.0d, evaluator.getLas());
    assertEquals(320, evaluator.getWordCount());
  }

  @Test
  void testParseValidatesArguments() {
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(null, new String[] {"DT"}));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[] {"the"}, null));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[0], new String[0]));
    assertThrows(IllegalArgumentException.class,
        () -> parser.parse(new String[] {"the"}, new String[] {"DT", "NN"}));
  }

  @Test
  void testConstructorRejectsNullModel() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyParserME((DependencyModel) null));
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyParserME((opennlp.tools.ml.model.MaxentModel) null));
  }

  @Test
  void testTrainValidatesArguments() {
    assertThrows(IllegalArgumentException.class,
        () -> DependencyParserME.train("eng", null, TrainingParameters.defaultParams()));
    assertThrows(IllegalArgumentException.class,
        () -> DependencyParserME.train("eng",
            ObjectStreamUtils.createObjectStream(corpus()), null));
    assertThrows(IllegalArgumentException.class,
        () -> DependencyParserME.train(null,
            ObjectStreamUtils.createObjectStream(corpus()),
            TrainingParameters.defaultParams()));
  }

  @Test
  void testModelRoundTripThroughSerialization() throws IOException {
    final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    model.serialize(out);
    final DependencyModel reloaded = new DependencyModel(
        new java.io.ByteArrayInputStream(out.toByteArray()));
    final DependencyGraph parsed = new DependencyParserME(reloaded)
        .parse(new String[] {"the", "dog", "barks"}, new String[] {"DT", "NN", "VBZ"});
    assertEquals(DependencyGraph.of(new int[] {1, 2, -1},
        new String[] {"det", "nsubj", "root"}), parsed);
  }

  @Test
  void testModelRejectsNullParserModel() {
    assertThrows(IllegalArgumentException.class,
        () -> new DependencyModel("eng", null, null));
  }
}
