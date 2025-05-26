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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.AbstractEvaluatorTool;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.namefind.TokenNameFinderEvaluatorTool.EvalToolParams;
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

/**
 * A default {@link NameSample}-centric implementation of {@link AbstractEvaluatorTool}
 * that prints to an output stream.
 *
 * @see AbstractEvaluatorTool
 * @see EvalToolParams
 */
public final class TokenNameFinderEvaluatorTool
    extends AbstractEvaluatorTool<NameSample, EvalToolParams> {

  interface EvalToolParams extends EvaluatorParams, FineGrainedEvaluatorParams {
    @OptionalParameter
    @ParameterDescription(valueName = "types", description = "name types to use for evaluation")
    String getNameTypes();
  }

  private static final Logger logger = LoggerFactory.getLogger(TokenNameFinderEvaluatorTool.class);

  public TokenNameFinderEvaluatorTool() {
    super(NameSample.class, EvalToolParams.class);
  }

  @Override
  public String getShortDescription() {
    return "Measures the performance of the NameFinder model with the reference data";
  }

  @Override
  public void run(String format, String[] args) {
    super.run(format, args);

    TokenNameFinderModel model = new TokenNameFinderModelLoader().load(params.getModel());

    List<EvaluationMonitor<NameSample>> listeners = new LinkedList<>();
    if (params.getMisclassified()) {
      listeners.add(new NameEvaluationErrorListener());
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
      String[] nameTypes = params.getNameTypes().split(",");
      sampleStream = new NameSampleTypeFilter(nameTypes, sampleStream);
    }

    TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(
        new NameFinderME(model),
        listeners.toArray(new TokenNameFinderEvaluationMonitor[0]));

    final PerformanceMonitor monitor = new PerformanceMonitor("sent");

    try (ObjectStream<NameSample> measuredSampleStream = new ObjectStream<>() {

      @Override
      public NameSample read() throws IOException {
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
      throw new TerminateToolException(-1, "IO error while reading test data: " + e.getMessage(), e);
    }
    // sorry that this can fail

    monitor.stopAndPrintFinalResult();

    if (reportFile != null) {
      reportListener.writeReport();
    }

    logger.info(evaluator.getFMeasure().toString());
  }
}
