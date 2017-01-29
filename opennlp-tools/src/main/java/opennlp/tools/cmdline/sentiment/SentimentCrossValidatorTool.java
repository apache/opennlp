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

package opennlp.tools.cmdline.sentiment;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import opennlp.tools.cmdline.AbstractCrossValidatorTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.BasicTrainingParams;
import opennlp.tools.cmdline.params.CVParams;
import opennlp.tools.cmdline.params.DetailedFMeasureEvaluatorParams;
import opennlp.tools.cmdline.sentiment.SentimentCrossValidatorTool.CVToolParams;
import opennlp.tools.sentiment.SentimentCrossValidator;
import opennlp.tools.sentiment.SentimentEvaluationMonitor;
import opennlp.tools.sentiment.SentimentFactory;
import opennlp.tools.sentiment.SentimentSample;
import opennlp.tools.util.eval.EvaluationMonitor;
import opennlp.tools.util.model.ModelUtil;

/**
 * Class for helping perform cross validation on the Sentiment Analysis Parser.
 */
public class SentimentCrossValidatorTool
    extends AbstractCrossValidatorTool<SentimentSample, CVToolParams> {

  /**
   * Interface for parameters
   */
  interface CVToolParams
      extends BasicTrainingParams, CVParams, DetailedFMeasureEvaluatorParams {

  }

  /**
   * Constructor
   */
  public SentimentCrossValidatorTool() {
    super(SentimentSample.class, CVToolParams.class);
  }

  /**
   * Returns the short description of the tool
   *
   * @return short description
   */
  public String getShortDescription() {
    return "K-fold cross validator for the learnable Sentiment Analysis Parser";
  }

  /**
   * Runs the tool
   *
   * @param format
   *          the format to be used
   * @param args
   *          the arguments
   */
  public void run(String format, String[] args) {
    super.run(format, args);

    mlParams = CmdLineUtil.loadTrainingParameters(params.getParams(), true);
    if (mlParams == null) {
      mlParams = ModelUtil.createDefaultTrainingParameters();
    }

    List<EvaluationMonitor<SentimentSample>> listeners = new LinkedList<EvaluationMonitor<SentimentSample>>();
    if (params.getMisclassified()) {
      listeners.add(new SentimentEvaluationErrorListener());
    }
    SentimentDetailedFMeasureListener detailedFListener = null;
    if (params.getDetailedF()) {
      detailedFListener = new SentimentDetailedFMeasureListener();
      listeners.add(detailedFListener);
    }

    SentimentFactory sentimentFactory = new SentimentFactory();

    SentimentCrossValidator validator;
    try {
      validator = new SentimentCrossValidator(params.getLang(), mlParams,
          sentimentFactory,
          listeners.toArray(new SentimentEvaluationMonitor[listeners.size()]));
      validator.evaluate(sampleStream, params.getFolds());
    } catch (IOException e) {
      throw new TerminateToolException(-1,
          "IO error while reading training data or indexing data: "
              + e.getMessage(),
          e);
    } finally {
      try {
        sampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    System.out.println("done");

    System.out.println();

    if (detailedFListener == null) {
      System.out.println(validator.getFMeasure());
    } else {
      System.out.println(detailedFListener.toString());
    }
  }

}
