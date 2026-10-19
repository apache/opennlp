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
import java.io.PrintWriter;
import java.util.function.Supplier;

import org.slf4j.Logger;

import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.depparse.DependencyEvaluationMonitor;
import opennlp.tools.depparse.DependencyEvaluator;
import opennlp.tools.depparse.DependencyParser;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.depparse.DependencyTree;
import opennlp.tools.depparse.FeedforwardDependencyModel;
import opennlp.tools.depparse.FeedforwardDependencyParser;
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.StringUtil;
import opennlp.tools.util.TrainingParameters;

/** The parsing, evaluation, and parameter steps shared by the dependency parser tools. */
final class DependencyParserToolSupport {

  /** Separates the fields of a CoNLL-U token row. */
  private static final String FIELD_SEPARATOR = "\t";

  /** Fills a CoNLL-U field that the parser does not predict. */
  private static final String EMPTY_FIELD = "_";

  private DependencyParserToolSupport() {
  }

  /**
   * Loads the training parameters file named on the command line, falling back to
   * {@code fallback} when none is given.
   *
   * @param paramsFile The parameters file name, or {@code null} if none was given.
   * @param fallback The parameters to use without a file. Must not be {@code null}.
   * @return The training parameters. Never {@code null}.
   */
  static TrainingParameters loadTrainingParameters(String paramsFile,
      TrainingParameters fallback) {
    TrainingParameters loaded = CmdLineUtil.loadTrainingParameters(paramsFile, false);
    return loaded == null ? fallback : loaded;
  }

  /**
   * Creates a feedforward parser with the requested beam size, or the beam size of the
   * model when none is requested.
   *
   * @param model The model to parse with. Must not be {@code null}.
   * @param beamSize The beam size given on the command line, or {@code null}.
   * @return The parser. Never {@code null}.
   * @throws TerminateToolException Thrown if the beam size is not positive.
   */
  static FeedforwardDependencyParser createFeedforwardParser(FeedforwardDependencyModel model,
      Integer beamSize) {
    try {
      return beamSize == null
          ? new FeedforwardDependencyParser(model) : new FeedforwardDependencyParser(model, beamSize);
    } catch (IllegalArgumentException e) {
      throw new TerminateToolException(-1, e.getMessage(), e);
    }
  }

  /**
   * Parses the POS-tagged sentences on standard input, one per line in the
   * {@code token_TAG} format, and prints each tree in basic CoNLL-U followed by a blank
   * line. Blank input lines are skipped.
   *
   * @param parser The parser to apply. Must not be {@code null}.
   */
  static void parseTaggedSentences(DependencyParser parser) {
    PrintWriter out = new PrintWriter(System.out, true);
    try (ObjectStream<String> lines = new PlainTextByLineStream(
        new SystemInputStreamFactory(), SystemInputStreamFactory.encoding())) {
      String line;
      while ((line = lines.read()) != null) {
        if (StringUtil.isBlank(line)) {
          continue;
        }
        POSSample sample = POSSample.parse(line);
        String[] tokens = sample.getSentence();
        String[] tags = sample.getTags();
        DependencyTree tree = parser.parse(tokens, tags);
        for (int i = 0; i < tokens.length; i++) {
          // Input tags may be UPOS or XPOS; retain them in XPOS without guessing a mapping.
          out.println(String.join(FIELD_SEPARATOR, Integer.toString(i + 1), tokens[i],
              EMPTY_FIELD, EMPTY_FIELD, tags[i], EMPTY_FIELD,
              Integer.toString(tree.headOf(i) + 1), tree.relationOf(i),
              EMPTY_FIELD, EMPTY_FIELD));
        }
        out.println();
      }
    } catch (IOException e) {
      CmdLineUtil.handleStdinIoError(e);
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
