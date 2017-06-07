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

import opennlp.tools.cmdline.AbstractEvaluatorTool;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.DetailedFMeasureEvaluatorParams;
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.cmdline.sentiment.SentimentEvaluatorTool.EvalToolParams;
import opennlp.tools.sentiment.SentimentEvaluationMonitor;
import opennlp.tools.sentiment.SentimentEvaluator;
import opennlp.tools.sentiment.SentimentME;
import opennlp.tools.sentiment.SentimentModel;
import opennlp.tools.sentiment.SentimentSample;
import opennlp.tools.sentiment.SentimentSampleTypeFilter;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.EvaluationMonitor;

/**
 * Class for creating an evaluation tool for sentiment analysis.
 */
public class SentimentEvaluatorTool
    extends AbstractEvaluatorTool<SentimentSample, EvalToolParams> {

  /**
   * Interface for parameters to be used in evaluation
   */
  interface EvalToolParams
      extends EvaluatorParams, DetailedFMeasureEvaluatorParams {
    @OptionalParameter
    @ParameterDescription(valueName = "types", description = "name types to use for evaluation")
    String getNameTypes();
  }

  /**
   * Constructor
   */
  public SentimentEvaluatorTool() {
    super(SentimentSample.class, EvalToolParams.class);
  }

  /**
   * Returns the short description of the tool
   *
   * @return short description
   */
  public String getShortDescription() {
    return "Measures the performance of the Sentiment model with the reference data";
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

    SentimentModel model = new SentimentModelLoader().load(params.getModel());
    // TODO: check EvalToolParams --> getNameTypes()

    List<EvaluationMonitor<SentimentSample>> listeners = new LinkedList<EvaluationMonitor<SentimentSample>>();
    if (params.getMisclassified()) {
      listeners.add(new SentimentEvaluationErrorListener());
    }
    SentimentDetailedFMeasureListener detailedFListener = null;
    if (params.getDetailedF()) {
      detailedFListener = new SentimentDetailedFMeasureListener();
      listeners.add(detailedFListener);
    }

    if (params.getNameTypes() != null) {
      String nameTypes[] = params.getNameTypes().split(",");
      sampleStream = new SentimentSampleTypeFilter(nameTypes, sampleStream);
    }

    SentimentEvaluator evaluator = new SentimentEvaluator(
        new SentimentME(model),
        listeners.toArray(new SentimentEvaluationMonitor[listeners.size()]));

    final PerformanceMonitor monitor = new PerformanceMonitor("sent");

    ObjectStream<SentimentSample> measuredSampleStream = new ObjectStream<SentimentSample>() {

      public SentimentSample read() throws IOException {
        SentimentSample sample = sampleStream.read();
        if (sample != null) {
          monitor.incrementCounter();
        }
        return sample;
      }

      public void reset() throws IOException {
        sampleStream.reset();
      }

      public void close() throws IOException {
        sampleStream.close();
      }
    };

    monitor.startAndPrintThroughput();

    try {
      evaluator.evaluate(measuredSampleStream);
    } catch (IOException e) {
      System.err.println("failed");
      throw new TerminateToolException(-1,
          "IO error while reading test data: " + e.getMessage(), e);
    } finally {
      try {
        measuredSampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    monitor.stopAndPrintFinalResult();

    System.out.println();

    if (detailedFListener == null) {
      System.out.println(evaluator.getFMeasure());
    } else {
      System.out.println(detailedFListener.toString());
    }
  }

}
