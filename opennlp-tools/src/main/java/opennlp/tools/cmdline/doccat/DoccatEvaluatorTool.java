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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * A default {@link DocumentSample}-centric implementation of {@link AbstractEvaluatorTool}
 * that prints to an output stream.
 *
 * @see AbstractEvaluatorTool
 * @see EvalToolParams
 */
public final class DoccatEvaluatorTool extends
    AbstractEvaluatorTool<DocumentSample, EvalToolParams> {

  interface EvalToolParams extends EvaluatorParams, FineGrainedEvaluatorParams {
  }

  private static final Logger logger = LoggerFactory.getLogger(DoccatEvaluatorTool.class);

  public DoccatEvaluatorTool() {
    super(DocumentSample.class, EvalToolParams.class);
  }

  @Override
  public String getShortDescription() {
    return "Measures the performance of the Doccat model with the reference data";
  }

  @Override
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
        listeners.toArray(new DoccatEvaluationMonitor[0]));

    final PerformanceMonitor monitor = new PerformanceMonitor("doc");

    try (ObjectStream<DocumentSample> measuredSampleStream = new ObjectStream<>() {

      @Override
      public DocumentSample read() throws IOException {
        monitor.incrementCounter();
        return sampleStream.read();
      }

      @Override
      public void reset() throws IOException {
        sampleStream.reset();
      }

      @Override
      public void close() throws IOException {
        sampleStream.close();
      }
    }) {
      monitor.startAndPrintThroughput();
      evaluator.evaluate(measuredSampleStream);
    } catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while reading test data: "
              + e.getMessage(), e);
    }
    // sorry that this can fail

    monitor.stopAndPrintFinalResult();

    logger.info(evaluator.toString());

    if (reportListener != null) {
      logger.info("Writing fine-grained report to {}",
          params.getReportOutputFile().getAbsolutePath());
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
