/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

package opennlp.tools.eval;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import opennlp.tools.depparse.DependencyEvaluator;
import opennlp.tools.depparse.DependencyModel;
import opennlp.tools.depparse.DependencyParserME;
import opennlp.tools.depparse.FeedforwardDependencyModel;
import opennlp.tools.depparse.FeedforwardDependencyParser;
import opennlp.tools.depparse.FeedforwardDependencyTrainer;
import opennlp.tools.formats.conllu.ConlluDependencySampleStream;
import opennlp.tools.formats.conllu.ConlluTagset;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.Parameters;
import opennlp.tools.util.TrainingParameters;

/**
 * Trains the transition-based and the feedforward dependency parser on the
 * {@code UD_English} training split of Universal Dependencies 2.0 and scores them on the
 * held-out development split, checking the unlabeled and labeled attachment scores.
 * The treebank's tokens and universal part-of-speech tags are the parser input, so the
 * scores measure parsing by itself.
 */
public class UniversalDependencyParserEval extends AbstractEvalTest {

  private static File englishTrain;
  private static File englishDev;

  /**
   * Locates the treebank splits and verifies their digests.
   *
   * @throws Exception Thrown if the data directory or a split is missing or changed.
   */
  @BeforeAll
  static void verifyTrainingData() throws Exception {
    englishTrain = new File(getOpennlpDataDir(), "ud20/UD_English/en-ud-train.conllu");
    englishDev = new File(getOpennlpDataDir(), "ud20/UD_English/en-ud-dev.conllu");
    verifyFileChecksum(englishTrain.toPath(),
        new BigInteger("240180699945832517506080812986784120023"));
    verifyFileChecksum(englishDev.toPath(),
        new BigInteger("77584448353380940340541536944256636015"));
  }

  /**
   * Trains the maximum-entropy transition parser and scores it on the development split.
   *
   * @throws IOException Thrown if reading or training fails.
   */
  @Test
  void trainAndEvalTransitionParserEnglish() throws IOException {
    final TrainingParameters parameters = TrainingParameters.defaultParams();
    parameters.put(Parameters.CUTOFF_PARAM, 5);
    final DependencyModel model;
    try (ConlluDependencySampleStream train = samples(englishTrain)) {
      model = DependencyParserME.train("eng", train, parameters);
    }
    final DependencyEvaluator evaluator = new DependencyEvaluator(new DependencyParserME(model));
    try (ConlluDependencySampleStream dev = samples(englishDev)) {
      evaluator.evaluate(dev);
    }
    Assertions.assertEquals(25148, evaluator.getWordCount());
    Assertions.assertEquals(0.8181565134404326d, evaluator.getUas(), ACCURACY_DELTA);
    Assertions.assertEquals(0.7861460155877207d, evaluator.getLas(), ACCURACY_DELTA);
  }

  /**
   * Trains the feedforward parser with its default settings and scores it on the
   * development split.
   *
   * @throws IOException Thrown if reading or training fails.
   */
  @Test
  void trainAndEvalFeedforwardParserEnglish() throws IOException {
    final FeedforwardDependencyModel model;
    try (ConlluDependencySampleStream train = samples(englishTrain)) {
      model = FeedforwardDependencyTrainer.train(train, FeedforwardDependencyTrainer.Settings.defaults());
    }
    final DependencyEvaluator evaluator =
        new DependencyEvaluator(new FeedforwardDependencyParser(model));
    try (ConlluDependencySampleStream dev = samples(englishDev)) {
      evaluator.evaluate(dev);
    }
    Assertions.assertEquals(25148, evaluator.getWordCount());
    Assertions.assertEquals(0.8412597423254334d, evaluator.getUas(), ACCURACY_DELTA);
    Assertions.assertEquals(0.8174009861619215d, evaluator.getLas(), ACCURACY_DELTA);
  }

  /**
   * Opens one split with the universal tagset.
   *
   * @param split The CoNLL-U file.
   * @return The sample stream.
   * @throws IOException Thrown if the file cannot be opened.
   */
  private static ConlluDependencySampleStream samples(File split) throws IOException {
    return new ConlluDependencySampleStream(new MarkableFileInputStreamFactory(split), ConlluTagset.U);
  }
}
