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

package opennlp.tools.cmdline.namefind;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import opennlp.tools.cmdline.AbstractCrossValidatorTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.namefind.TokenNameFinderCrossValidatorTool.CVToolParams;
import opennlp.tools.cmdline.params.CVParams;
import opennlp.tools.cmdline.params.DetailedFMeasureEvaluatorParams;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinderCrossValidator;
import opennlp.tools.namefind.TokenNameFinderEvaluationMonitor;
import opennlp.tools.util.eval.EvaluationMonitor;
import opennlp.tools.util.model.ModelUtil;

public final class TokenNameFinderCrossValidatorTool
    extends AbstractCrossValidatorTool<NameSample, CVToolParams> {
  
  interface CVToolParams extends TrainingParams, CVParams, DetailedFMeasureEvaluatorParams {
  }

  public TokenNameFinderCrossValidatorTool() {
    super(NameSample.class, CVToolParams.class);
  }

  public String getShortDescription() {
    return "K-fold cross validator for the learnable Name Finder";
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);
    if (mlParams == null) {
      mlParams = ModelUtil.createTrainingParameters(params.getIterations(), params.getCutoff());
    }

    byte featureGeneratorBytes[] =
        TokenNameFinderTrainerTool.openFeatureGeneratorBytes(params.getFeaturegen());

    Map<String, Object> resources =
        TokenNameFinderTrainerTool.loadResources(params.getResources());

    List<EvaluationMonitor<NameSample>> listeners = new LinkedList<EvaluationMonitor<NameSample>>();
    if (params.getMisclassified()) {
      listeners.add(new NameEvaluationErrorListener());
    }
    TokenNameFinderDetailedFMeasureListener detailedFListener = null;
    if (params.getDetailedF()) {
      detailedFListener = new TokenNameFinderDetailedFMeasureListener();
      listeners.add(detailedFListener);
    }

    TokenNameFinderCrossValidator validator;
    try {
      validator = new TokenNameFinderCrossValidator(params.getLang(),
          params.getType(), mlParams, featureGeneratorBytes, resources,
          listeners.toArray(new TokenNameFinderEvaluationMonitor[listeners.size()]));
      validator.evaluate(sampleStream, params.getFolds());
    } catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while reading training data or indexing data: "
          + e.getMessage(), e);
    } finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    System.out.println("done");

    System.out.println();
    
    if(detailedFListener == null) {
      System.out.println(validator.getFMeasure());
    } else {
      System.out.println(detailedFListener.toString());
    }
  }
}
