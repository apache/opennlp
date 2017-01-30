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

package opennlp.tools.cmdline.doccat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import opennlp.tools.cmdline.AbstractEvaluatorTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.doccat.DoccatEvaluatorTool.EvalToolParams;
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.cmdline.params.FineGrainedEvaluatorParams;
import opennlp.tools.doccat.DoccatEvaluationMonitor;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerEvaluator;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.EvaluationMonitor;

public final class DoccatEvaluatorTool extends
    AbstractEvaluatorTool<DocumentSample, EvalToolParams> {

  interface EvalToolParams extends EvaluatorParams, FineGrainedEvaluatorParams {
  }

  public DoccatEvaluatorTool() {
    super(DocumentSample.class, EvalToolParams.class);
  }

  public String getShortDescription() {
    return "Measures the performance of the Doccat model with the reference data";
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    DoccatModel model = new DoccatModelLoader().load(params.getModel());

    List<EvaluationMonitor<DocumentSample>> listeners = new LinkedList<>();
    if (params.getMisclassified()) {
      listeners.add(new DoccatEvaluationErrorListener());
    }

    DoccatFineGrainedReportListener reportListener = null;
    File reportFile = params.getReportOutputFile();
    OutputStream reportOutputStream = null;
    if (reportFile != null) {
      CmdLineUtil.checkOutputFile("Report Output File", reportFile);
      try {
        reportOutputStream = new FileOutputStream(reportFile);
        reportListener = new DoccatFineGrainedReportListener(reportOutputStream);
        listeners.add(reportListener);
      } catch (FileNotFoundException e) {
        throw new TerminateToolException(-1,
            "IO error while creating Doccat fine-grained report file: "
                + e.getMessage());
      }
    }

    DocumentCategorizerEvaluator evaluator = new DocumentCategorizerEvaluator(
        new DocumentCategorizerME(model),
        listeners.toArray(new DoccatEvaluationMonitor[listeners.size()]));

    final PerformanceMonitor monitor = new PerformanceMonitor("doc");

    ObjectStream<DocumentSample> measuredSampleStream = new ObjectStream<DocumentSample>() {

      public DocumentSample read() throws IOException {
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
      throw new TerminateToolException(-1, "IO error while reading test data: "
          + e.getMessage(), e);
    } finally {
      try {
        measuredSampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    monitor.stopAndPrintFinalResult();

    System.out.println();

    System.out.println(evaluator);

    if (reportListener != null) {
      System.out.println("Writing fine-grained report to "
          + params.getReportOutputFile().getAbsolutePath());
      reportListener.writeReport();

      try {
        // TODO: is it a problem to close the stream now?
        reportOutputStream.close();
      } catch (IOException e) {
        // nothing to do
      }
    }
  }
}
