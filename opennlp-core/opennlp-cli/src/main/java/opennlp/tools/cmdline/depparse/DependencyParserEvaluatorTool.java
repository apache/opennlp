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

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.AbstractEvaluatorTool;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.depparse.DependencyParserEvaluatorTool.EvalParams;
import opennlp.tools.depparse.DependencyEvaluator;
import opennlp.tools.depparse.DependencyParserME;
import opennlp.tools.depparse.DependencySample;
import opennlp.tools.util.ObjectStream;

/** Reports labeled and unlabeled attachment scores against gold CoNLL-U trees. */
public class DependencyParserEvaluatorTool extends AbstractEvaluatorTool<DependencySample, EvalParams> {

  interface EvalParams {
    @ParameterDescription(valueName = "model", description = "the dependency parser model file")
    File getModel();
  }

  private static final Logger logger = LoggerFactory.getLogger(DependencyParserEvaluatorTool.class);

  /** Creates the evaluator tool. */
  public DependencyParserEvaluatorTool() {
    super(DependencySample.class, EvalParams.class);
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
    try (ObjectStream<DependencySample> samples = sampleStream) {
      DependencyEvaluator evaluator = new DependencyEvaluator(new DependencyParserME(
          new DependencyModelLoader().load(params.getModel())));
      evaluator.evaluate(samples);
      logger.info("Tokens: {}; UAS: {}; LAS: {}", evaluator.getWordCount(),
          evaluator.getUas(), evaluator.getLas());
    } catch (IOException e) {
      throw new TerminateToolException(-1, "Error reading dependency evaluation data", e);
    }
  }
}
