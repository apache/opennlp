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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.AbstractCrossValidatorTool;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.depparse.DependencyParserCrossValidatorTool.CrossValidationParams;
import opennlp.tools.cmdline.params.BasicTrainingParams;
import opennlp.tools.cmdline.params.CVParams;
import opennlp.tools.depparse.DependencyCrossValidator;
import opennlp.tools.depparse.DependencyEvaluationMonitor;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.util.ObjectStream;

/** Cross validates an arc-standard dependency parser on CoNLL-U samples. */
public class DependencyParserCrossValidatorTool
    extends AbstractCrossValidatorTool<DependencySample, CrossValidationParams> {

  interface CrossValidationParams extends CVParams, BasicTrainingParams {
  }

  private static final Logger logger = LoggerFactory.getLogger(DependencyParserCrossValidatorTool.class);

  /** Creates the cross validator tool. */
  public DependencyParserCrossValidatorTool() {
    super(DependencySample.class, CrossValidationParams.class);
  }

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Cross validates a dependency parser";
  }

  /** {@inheritDoc} */
  @Override
  public void run(String format, String[] args) {
    super.run(format, args);
    try (ObjectStream<DependencySample> samples = sampleStream) {
      mlParams = DependencyParserTrainerTool.loadTrainingParameters(params.getParams());
      DependencyEvaluationMonitor errorListener = null;
      if (params.getMisclassified()) {
        errorListener = new DependencyEvaluationErrorListener();
      }
      DependencyCrossValidator validator =
          new DependencyCrossValidator(params.getLang(), mlParams, errorListener);
      validator.evaluate(samples, params.getFolds());
      logger.info("Tokens: {}; UAS: {}; LAS: {}", validator.getWordCount(),
          validator.getUas(), validator.getLas());
      logger.info("Tokens excluding punctuation: {}; UAS: {}; LAS: {}",
          validator.getWordCountExcludingPunctuation(),
          validator.getUasExcludingPunctuation(), validator.getLasExcludingPunctuation());
    } catch (IOException e) {
      throw createTerminationIOException(e);
    } catch (IllegalArgumentException e) {
      throw new TerminateToolException(-1, e.getMessage(), e);
    }
  }
}
