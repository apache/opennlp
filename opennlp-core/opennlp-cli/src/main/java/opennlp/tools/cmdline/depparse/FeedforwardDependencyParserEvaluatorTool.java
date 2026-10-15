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

import opennlp.tools.cmdline.AbstractEvaluatorTool;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.depparse.FeedforwardDependencyParserEvaluatorTool.EvalToolParams;
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.depparse.FeedforwardDependencyParser;

/**
 * Reports labeled and unlabeled attachment scores of a {@link FeedforwardDependencyParser}
 * against gold trees.
 */
public class FeedforwardDependencyParserEvaluatorTool
    extends AbstractEvaluatorTool<DependencySample, EvalToolParams> {

  /** The evaluator parameters plus the beam size to parse with. */
  interface EvalToolParams extends EvaluatorParams {

    @ParameterDescription(valueName = "n", description = "the number of transition sequences "
        + "to keep per step. Defaults to the beam size the model was refined with, or 1 "
        + "(greedy) for a model that was not refined.")
    @OptionalParameter
    Integer getBeamSize();
  }

  private static final Logger logger =
      LoggerFactory.getLogger(FeedforwardDependencyParserEvaluatorTool.class);

  /** Creates the evaluator tool. */
  public FeedforwardDependencyParserEvaluatorTool() {
    super(DependencySample.class, EvalToolParams.class);
  }

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Measures feedforward dependency parser attachment scores";
  }

  /** {@inheritDoc} */
  @Override
  public void run(String format, String[] args) {
    super.run(format, args);
    try {
      DependencyParserEvaluatorTool.evaluate(
          () -> FeedforwardDependencyParserTool.createParser(
              new FeedforwardDependencyModelLoader().load(params.getModel()), params.getBeamSize()),
          sampleStream, params.getMisclassified(), logger);
    } catch (IOException e) {
      throw createTerminationIOException(e);
    }
  }
}
