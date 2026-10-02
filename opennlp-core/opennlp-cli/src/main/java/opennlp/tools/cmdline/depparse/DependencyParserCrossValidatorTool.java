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
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.depparse.DependencyParserCrossValidatorTool.CrossValidationParams;
import opennlp.tools.depparse.DependencyCrossValidator;
import opennlp.tools.depparse.DependencyParserFactory;
import opennlp.tools.depparse.DependencyParserME;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/** Cross validates an arc-standard dependency parser on CoNLL-U samples. */
public class DependencyParserCrossValidatorTool
    extends AbstractCrossValidatorTool<DependencySample, CrossValidationParams> {

  interface CrossValidationParams extends TrainingParams {
    @ParameterDescription(valueName = "num", description = "number of folds, at least two")
    @OptionalParameter(defaultValue = "10")
    Integer getFolds();
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
      if (params.getFolds() < 2) {
        throw new TerminateToolException(-1, "The number of folds must be at least two");
      }
      mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);
      if (mlParams == null) {
        mlParams = TrainingParameters.defaultParams();
      }
      DependencyCrossValidator validator = new DependencyCrossValidator(training ->
          new DependencyParserME(DependencyParserME.train(params.getLang(), training, mlParams,
              DependencyParserFactory.create(params.getFactory()))));
      validator.evaluate(samples, params.getFolds());
      logger.info("Tokens: {}; UAS: {}; LAS: {}", validator.getWordCount(),
          validator.getUas(), validator.getLas());
    } catch (IOException e) {
      throw createTerminationIOException(e);
    }
  }
}
