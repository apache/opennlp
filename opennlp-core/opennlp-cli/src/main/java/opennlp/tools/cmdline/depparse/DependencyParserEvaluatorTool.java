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

import java.io.IOException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.AbstractEvaluatorTool;
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.depparse.DependencyEvaluationMonitor;
import opennlp.tools.depparse.DependencyEvaluator;
import opennlp.tools.depparse.DependencyParser;
import opennlp.tools.depparse.DependencyParserME;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.util.ObjectStream;

/** Reports labeled and unlabeled attachment scores against gold CoNLL-U trees. */
public class DependencyParserEvaluatorTool
    extends AbstractEvaluatorTool<DependencySample, EvaluatorParams> {

  private static final Logger logger = LoggerFactory.getLogger(DependencyParserEvaluatorTool.class);

  /** Creates the evaluator tool. */
  public DependencyParserEvaluatorTool() {
    super(DependencySample.class, EvaluatorParams.class);
  }

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Measures dependency parser attachment scores";
  }

  /** {@inheritDoc} */
  @Override
  public void run(String format, String[] args) {
    super.run(format, args);
    try {
      evaluate(() -> new DependencyParserME(new DependencyModelLoader().load(params.getModel())),
          sampleStream, params.getMisclassified(), logger);
    } catch (IOException e) {
      throw createTerminationIOException(e);
    }
  }

  /**
   * Scores a parser on samples and logs the attachment scores over all tokens and over
   * the tokens that are not punctuation. Closes the samples, also when the parser
   * cannot be created.
   *
   * @param parser Creates the parser to score, typically by loading its model. Must not
   *               be {@code null}.
   * @param samples The gold samples. Must not be {@code null}.
   * @param misclassified Whether to print each misparsed sentence next to its gold tree.
   * @param logger The logger to report the scores to. Must not be {@code null}.
   * @throws IOException Thrown if reading the samples fails.
   */
  static void evaluate(Supplier<DependencyParser> parser, ObjectStream<DependencySample> samples,
      boolean misclassified, Logger logger) throws IOException {
    try (samples) {
      DependencyEvaluationMonitor errorListener = null;
      if (misclassified) {
        errorListener = new DependencyEvaluationErrorListener();
      }
      DependencyEvaluator evaluator = new DependencyEvaluator(parser.get(), errorListener);
      evaluator.evaluate(samples);
      logger.info("Tokens: {}; UAS: {}; LAS: {}", evaluator.getWordCount(),
          evaluator.getUas(), evaluator.getLas());
      logger.info("Tokens excluding punctuation: {}; UAS: {}; LAS: {}",
          evaluator.getWordCountExcludingPunctuation(),
          evaluator.getUasExcludingPunctuation(), evaluator.getLasExcludingPunctuation());
    }
  }
}
