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
package opennlp.tools.cmdline.languagemodel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.languagemodel.NGramLanguageModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.StringList;

/**
 * Command line tool for {@link opennlp.tools.languagemodel.LanguageModel#calculateProbability(StringList)}.
 */
public class LanguageModelTool extends BasicCmdLineTool {

  @Override
  public String getShortDescription() {
    return "gives the probability of a sequence of tokens in a language model";
  }

  @Override
  public void run(String[] args) {
    File lmFile = new File(args[0]);
    FileInputStream stream = null;
    try {
      stream = new FileInputStream(lmFile);
      NGramLanguageModel nGramLanguageModel = new NGramLanguageModel(
          stream);

      ObjectStream<String> lineStream;
      PerformanceMonitor perfMon = null;

      try {
        lineStream = new PlainTextByLineStream(
            new SystemInputStreamFactory(),
            SystemInputStreamFactory.encoding());
        perfMon = new PerformanceMonitor(System.err, "lm");
        perfMon.start();
        String line;
        while ((line = lineStream.read()) != null) {
          double probability;
          String[] tokens = line.split(" ");
          try {
            probability = nGramLanguageModel
                .calculateProbability(new StringList(tokens));
          } catch (Exception e) {
            System.err.println("Error:" + e.getLocalizedMessage());
            System.err.println(line);
            continue;
          }

          System.out.println("sequence '" + Arrays.toString(tokens)
              + "' has a probability of " + probability);

          perfMon.incrementCounter();
        }
      } catch (IOException e) {
        CmdLineUtil.handleStdinIoError(e);
      }

      perfMon.stopAndPrintFinalResult();

    } catch (java.io.IOException e) {
      System.err.println(e.getLocalizedMessage());
    } finally {
      if (stream != null) {
        try {
          stream.close();
        } catch (IOException e) {
          // do nothing
        }
      }
    }
  }

  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model";
  }
}
