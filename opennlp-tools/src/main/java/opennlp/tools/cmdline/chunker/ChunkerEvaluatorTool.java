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

package opennlp.tools.cmdline.chunker;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.ChunkerEvaluationMonitor;
import opennlp.tools.chunker.ChunkerEvaluator;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.cmdline.AbstractEvaluatorTool;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.chunker.ChunkerEvaluatorTool.EvalToolParams;
import opennlp.tools.cmdline.params.DetailedFMeasureEvaluatorParams;
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.EvaluationMonitor;

public final class ChunkerEvaluatorTool
    extends AbstractEvaluatorTool<ChunkSample, EvalToolParams> {

  interface EvalToolParams extends EvaluatorParams, DetailedFMeasureEvaluatorParams {
  }

  public ChunkerEvaluatorTool() {
    super(ChunkSample.class, EvalToolParams.class);
  }

  public String getShortDescription() {
    return "Measures the performance of the Chunker model with the reference data";
  }

  public void run(String format, String[] args) {
    super.run(format, args);

    ChunkerModel model = new ChunkerModelLoader().load(params.getModel());

    List<EvaluationMonitor<ChunkSample>> listeners = new LinkedList<>();
    ChunkerDetailedFMeasureListener detailedFMeasureListener = null;
    if (params.getMisclassified()) {
      listeners.add(new ChunkEvaluationErrorListener());
    }
    if (params.getDetailedF()) {
      detailedFMeasureListener = new ChunkerDetailedFMeasureListener();
      listeners.add(detailedFMeasureListener);
    }

    ChunkerEvaluator evaluator = new ChunkerEvaluator(new ChunkerME(model),
        listeners.toArray(new ChunkerEvaluationMonitor[listeners.size()]));

    final PerformanceMonitor monitor = new PerformanceMonitor("sent");

    ObjectStream<ChunkSample> measuredSampleStream = new ObjectStream<ChunkSample>() {

      public ChunkSample read() throws IOException {
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

    if (detailedFMeasureListener == null) {
      System.out.println(evaluator.getFMeasure());
    } else {
      System.out.println(detailedFMeasureListener.toString());
    }
  }
}
