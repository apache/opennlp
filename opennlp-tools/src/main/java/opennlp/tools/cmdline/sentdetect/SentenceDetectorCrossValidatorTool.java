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

package opennlp.tools.cmdline.sentdetect;

import java.io.IOException;

import opennlp.tools.cmdline.AbstractCrossValidatorTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.params.CVParams;
import opennlp.tools.cmdline.sentdetect.SentenceDetectorCrossValidatorTool.CVToolParams;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.sentdetect.SDCrossValidator;
import opennlp.tools.sentdetect.SentenceDetectorEvaluationMonitor;
import opennlp.tools.sentdetect.SentenceDetectorFactory;
import opennlp.tools.sentdetect.SentenceSample;
import opennlp.tools.sentdetect.SentenceSampleStream;
import opennlp.tools.util.eval.FMeasure;
import opennlp.tools.util.model.ModelUtil;

public final class SentenceDetectorCrossValidatorTool
    extends AbstractCrossValidatorTool<SentenceSample, CVToolParams> {

  interface CVToolParams extends TrainingParams, CVParams {
  }

  public SentenceDetectorCrossValidatorTool() {
    super(SentenceSample.class, CVToolParams.class);
  }

  public String getShortDescription() {
    return "K-fold cross validator for the learnable sentence detector";
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), false);
    if (mlParams == null) {
      mlParams = ModelUtil.createDefaultTrainingParameters();
    }

    SDCrossValidator validator;

    SentenceDetectorEvaluationMonitor errorListener = null;
    if (params.getMisclassified()) {
      errorListener = new SentenceEvaluationErrorListener();
    }

    char[] eos = null;
    if (params.getEosChars() != null) {
      String eosString = SentenceSampleStream.replaceNewLineEscapeTags(params.getEosChars());
      eos = eosString.toCharArray();
    }

    try {
      Dictionary abbreviations = SentenceDetectorTrainerTool.loadDict(params.getAbbDict());
      SentenceDetectorFactory sdFactory = SentenceDetectorFactory.create(
          params.getFactory(), params.getLang(), true, abbreviations, eos);
      validator = new SDCrossValidator(params.getLang(), mlParams, sdFactory,
          errorListener);

      validator.evaluate(sampleStream, params.getFolds());
    }
    catch (IOException e) {
      throw createTerminationIOException(e);
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    FMeasure result = validator.getFMeasure();

    System.out.println(result.toString());
  }
}
