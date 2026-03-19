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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.AbstractEvaluatorTool;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.doccat.DoccatSVMEvaluatorTool.EvalToolParams;
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.doccat.BagOfWordsFeatureGenerator;
import opennlp.tools.doccat.DoccatEvaluationMonitor;
import opennlp.tools.doccat.DocumentCategorizerEvaluator;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.ml.libsvm.doccat.SvmDoccatModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.EvaluationMonitor;

/**
 * CLI tool for evaluating an SVM-based document categorization model.
 * <p>
 * Usage: {@code opennlp DoccatSVMEvaluator -model model -data testData}
 */
public final class DoccatSVMEvaluatorTool extends
    AbstractEvaluatorTool<DocumentSample, EvalToolParams> {

  interface EvalToolParams extends EvaluatorParams {
  }

  private static final Logger logger = LoggerFactory.getLogger(DoccatSVMEvaluatorTool.class);

  public DoccatSVMEvaluatorTool() {
    super(DocumentSample.class, EvalToolParams.class);
  }

  @Override
  public String getShortDescription() {
    return "Measures the performance of the SVM Doccat model with the reference data";
  }

  @Override
  public void run(String format, String[] args) {
    super.run(format, args);

    SvmDoccatModel model;
    try (FileInputStream in = new FileInputStream(params.getModel())) {
      model = SvmDoccatModel.deserialize(in);
    } catch (IOException | ClassNotFoundException e) {
      throw new TerminateToolException(-1,
          "Failed to load SVM Doccat model: " + e.getMessage(), e);
    }

    List<EvaluationMonitor<DocumentSample>> listeners = new LinkedList<>();
    if (params.getMisclassified()) {
      listeners.add(new DoccatEvaluationErrorListener());
    }

    opennlp.tools.ml.libsvm.doccat.DocumentCategorizerSVM categorizer =
        new opennlp.tools.ml.libsvm.doccat.DocumentCategorizerSVM(
            model, new BagOfWordsFeatureGenerator());

    DocumentCategorizerEvaluator evaluator = new DocumentCategorizerEvaluator(
        categorizer, listeners.toArray(new DoccatEvaluationMonitor[0]));

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
      throw new TerminateToolException(-1,
          "IO error while reading test data: " + e.getMessage(), e);
    }

    monitor.stopAndPrintFinalResult();

    logger.info(evaluator.toString());
  }
}
