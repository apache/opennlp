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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import opennlp.tools.cmdline.AbstractEvaluatorTool;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.namefind.TokenNameFinderEvaluatorTool.EvalToolParams;
import opennlp.tools.cmdline.params.DetailedFMeasureEvaluatorParams;
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.cmdline.params.FineGrainedEvaluatorParams;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleTypeFilter;
import opennlp.tools.namefind.TokenNameFinderEvaluationMonitor;
import opennlp.tools.namefind.TokenNameFinderEvaluator;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.EvaluationMonitor;

public final class TokenNameFinderEvaluatorTool
    extends AbstractEvaluatorTool<NameSample, EvalToolParams> {

  interface EvalToolParams extends EvaluatorParams,
      DetailedFMeasureEvaluatorParams, FineGrainedEvaluatorParams {
    @OptionalParameter
    @ParameterDescription(valueName = "types", description = "name types to use for evaluation")
    String getNameTypes();
  }

  public TokenNameFinderEvaluatorTool() {
    super(NameSample.class, EvalToolParams.class);
  }

  public String getShortDescription() {
    return "Measures the performance of the NameFinder model with the reference data";
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    TokenNameFinderModel model = new TokenNameFinderModelLoader().load(params.getModel());

    List<EvaluationMonitor<NameSample>> listeners = new LinkedList<EvaluationMonitor<NameSample>>();
    if (params.getMisclassified()) {
      listeners.add(new NameEvaluationErrorListener());
    }
    TokenNameFinderDetailedFMeasureListener detailedFListener = null;
    if (params.getDetailedF()) {
      detailedFListener = new TokenNameFinderDetailedFMeasureListener();
      listeners.add(detailedFListener);
    }

    TokenNameFinderFineGrainedReportListener reportListener = null;
    File reportFile = params.getReportOutputFile();
    OutputStream reportOutputStream = null;

    if (reportFile != null) {
      CmdLineUtil.checkOutputFile("Report Output File", reportFile);
      try {
        reportOutputStream = new FileOutputStream(reportFile);
        reportListener = new TokenNameFinderFineGrainedReportListener(model.getSequenceCodec(),
            reportOutputStream);
        listeners.add(reportListener);
      } catch (FileNotFoundException e) {
        throw new TerminateToolException(-1,
            "IO error while creating Name Finder fine-grained report file: "
                + e.getMessage());
      }
    }

    if (params.getNameTypes() != null) {
      String nameTypes[] = params.getNameTypes().split(",");
      sampleStream = new NameSampleTypeFilter(nameTypes, sampleStream);
    }

    TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(
        new NameFinderME(model),
        listeners.toArray(new TokenNameFinderEvaluationMonitor[listeners.size()]));

    final PerformanceMonitor monitor = new PerformanceMonitor("sent");

    ObjectStream<NameSample> measuredSampleStream = new ObjectStream<NameSample>() {

      public NameSample read() throws IOException {
        monitor.incrementCounter();
        return sampleStream.read();
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
      throw new TerminateToolException(-1, "IO error while reading test data: " + e.getMessage(), e);
    } finally {
      try {
        measuredSampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    monitor.stopAndPrintFinalResult();

    System.out.println();

    if (reportFile != null) {
      reportListener.writeReport();
    }

    if (detailedFListener == null) {
      System.out.println(evaluator.getFMeasure());
    } else {
      System.out.println(detailedFListener.toString());
    }
  }
}
