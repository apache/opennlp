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

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import opennlp.tools.depparse.DependencyEvaluator;
import opennlp.tools.depparse.DependencyModel;
import opennlp.tools.depparse.DependencyParserME;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trains the greedy arc-standard parser on a Universal Dependencies treebank and scores
 * it on the treebank's test split, reporting UAS and LAS.
 *
 * <p>Runs only when {@code opennlp.depparse.ud.dir} names a directory containing
 * {@code train.conllu} and {@code test.conllu} (a UD treebank's splits, renamed or
 * linked). The score uses the treebank's sentence boundaries, tokens, and tags. The
 * data is downloaded by the runner and is not included in the repository. Check the
 * treebank license before distributing a trained model.</p>
 */
public class ConlluDependencyParserEvalTest {

  @Test
  @EnabledIfSystemProperty(named = "opennlp.depparse.ud.dir", matches = ".+")
  void testTrainAndScoreOnUniversalDependencies() throws IOException {
    final Path dir = Path.of(System.getProperty("opennlp.depparse.ud.dir"));

    final TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, 5);
    final long trainStart = System.currentTimeMillis();
    final DependencyModel model;
    try (ConlluDependencySampleStream train = samples(dir.resolve("train.conllu"))) {
      model = DependencyParserME.train("eng", train, parameters);
    }
    System.out.println("Training time: " + (System.currentTimeMillis() - trainStart) + " ms");

    final DependencyEvaluator evaluator =
        new DependencyEvaluator(new DependencyParserME(model));
    try (ConlluDependencySampleStream test = samples(dir.resolve("test.conllu"))) {
      evaluator.evaluate(test);
    }
    System.out.println("UAS: " + evaluator.getUas());
    System.out.println("LAS: " + evaluator.getLas());
    System.out.println("Tokens: " + evaluator.getWordCount());

    // Detect a substantial accuracy regression while reporting the exact scores above.
    assertTrue(evaluator.getUas() > 0.6d, "UAS regressed below the floor");
    assertTrue(evaluator.getLas() > 0.5d, "LAS regressed below the floor");
  }

  /** Opens a sample stream over one CoNLL-U split using the universal tagset. */
  private static ConlluDependencySampleStream samples(Path conllu) throws IOException {
    final InputStreamFactory in = new MarkableFileInputStreamFactory(conllu.toFile());
    return new ConlluDependencySampleStream(in, ConlluTagset.U);
  }
}
