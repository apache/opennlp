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

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import opennlp.tools.chunker.ChunkSample;
import opennlp.tools.chunker.ChunkerEvaluator;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.BasicEvaluationParameters;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.util.ObjectStream;

public final class ChunkerEvaluatorTool implements CmdLineTool {

  public String getName() {
    return "ChunkerEvaluator";
  }

  public String getShortDescription() {
    return "Measures the performance of the Chunker model with the reference data";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + ArgumentParser.createUsage(BasicEvaluationParameters.class);
  }

  public void run(String[] args) {

  	if (!ArgumentParser.validateArguments(args, BasicEvaluationParameters.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }
  	
  	BasicEvaluationParameters params = ArgumentParser.parse(args, BasicEvaluationParameters.class);
  	
  	File testData =params.getData();

    CmdLineUtil.checkInputFile("Test data", testData);

    Charset encoding = params.getEncoding();

    ChunkerModel model = new ChunkerModelLoader().load(params.getModel());

    ChunkerEvaluator evaluator = new ChunkerEvaluator(new ChunkerME(model), params.getMisclassified());
    
    final ObjectStream<ChunkSample> sampleStream = ChunkerTrainerTool.openSampleData("Test",
        testData, encoding);

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
      System.err.println("Reading test data error " + e.getMessage());
      throw new TerminateToolException(-1);
    } finally {
      try {
        measuredSampleStream.close();
      } catch (IOException e) {
        // sorry that this can fail
      }
    }

    monitor.stopAndPrintFinalResult();

    System.out.println();

    System.out.println(evaluator.getFMeasure());
  }
}
