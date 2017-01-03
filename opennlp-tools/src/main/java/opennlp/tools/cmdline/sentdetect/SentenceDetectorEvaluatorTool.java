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

import opennlp.tools.cmdline.AbstractEvaluatorTool;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.cmdline.sentdetect.SentenceDetectorEvaluatorTool.EvalToolParams;
import opennlp.tools.sentdetect.SentenceDetectorEvaluationMonitor;
import opennlp.tools.sentdetect.SentenceDetectorEvaluator;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.sentdetect.SentenceSample;

public final class SentenceDetectorEvaluatorTool
    extends AbstractEvaluatorTool<SentenceSample, EvalToolParams> {

  interface EvalToolParams extends EvaluatorParams {
  }

  public SentenceDetectorEvaluatorTool() {
    super(SentenceSample.class, EvalToolParams.class);
  }

  public String getShortDescription() {
    return "evaluator for the learnable sentence detector";
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    SentenceModel model = new SentenceModelLoader().load(params.getModel());

    SentenceDetectorEvaluationMonitor errorListener = null;
    if (params.getMisclassified()) {
      errorListener = new SentenceEvaluationErrorListener();
    }

    SentenceDetectorEvaluator evaluator = new SentenceDetectorEvaluator(
        new SentenceDetectorME(model), errorListener);

    System.out.print("Evaluating ... ");
    try {
      evaluator.evaluate(sampleStream);
    }
    catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while reading training data or indexing data: " +
          e.getMessage(), e);
    }
    finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    System.err.println("done");

    System.out.println();

    System.out.println(evaluator.getFMeasure());
  }
}
