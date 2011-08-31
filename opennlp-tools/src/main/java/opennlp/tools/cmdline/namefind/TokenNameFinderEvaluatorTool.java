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
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.params.DetailedFMeasureEvaluatorParams;
import opennlp.tools.cmdline.params.EvaluatorParams;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinderEvaluationMonitor;
import opennlp.tools.namefind.TokenNameFinderEvaluator;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.eval.EvaluationMonitor;

public final class TokenNameFinderEvaluatorTool implements CmdLineTool {

  interface EvalToolParams extends EvaluatorParams, DetailedFMeasureEvaluatorParams {
    
  }
  
  public String getName() {
    return "TokenNameFinderEvaluator";
  }

  public String getShortDescription() {
    return "";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " "
        + ArgumentParser.createUsage(EvalToolParams.class);
  }

  public void run(String[] args) {

    if (!ArgumentParser
        .validateArguments(args, EvalToolParams.class)) {
      System.err.println(getHelp());
      throw new TerminateToolException(1);
    }

    EvalToolParams params = ArgumentParser.parse(args,
        EvalToolParams.class);

    File testData = params.getData();
    CmdLineUtil.checkInputFile("Test data", testData);

    Charset encoding = params.getEncoding();

    TokenNameFinderModel model = new TokenNameFinderModelLoader().load(params
        .getModel());
    
    List<EvaluationMonitor<NameSample>> listeners = new LinkedList<EvaluationMonitor<NameSample>>();
    if (params.getMisclassified()) {
      listeners.add(new NameEvaluationErrorListener());
    }
    TokenNameFinderDetailedFMeasureListener detailedFListener = null;
    if (params.getDetailedF()) {
      detailedFListener = new TokenNameFinderDetailedFMeasureListener();
      listeners.add(detailedFListener);
    }

    TokenNameFinderEvaluator evaluator = new TokenNameFinderEvaluator(
        new NameFinderME(model),
        listeners.toArray(new TokenNameFinderEvaluationMonitor[listeners.size()]));

    final ObjectStream<NameSample> sampleStream = TokenNameFinderTrainerTool.openSampleData("Test",
        testData, encoding);

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

    if(detailedFListener == null) {
      System.out.println(evaluator.getFMeasure());
    } else {
      System.out.println(detailedFListener.toString());
    }
  }
}
