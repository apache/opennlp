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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.languagemodel.NGramLanguageModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * Command line tool for {@link opennlp.tools.languagemodel.NGramLanguageModel}.
 */
public class NGramLanguageModelTool extends BasicCmdLineTool {

  private static final Logger logger = LoggerFactory.getLogger(NGramLanguageModelTool.class);

  @Override
  public String getShortDescription() {
    return "Gives the probability and most probable next token(s) of a sequence of tokens in a " +
        "language model";
  }

  @Override
  public void run(String[] args) {
    File lmFile = new File(args[0]);
    try (InputStream stream = new BufferedInputStream(new FileInputStream(lmFile))) {
      NGramLanguageModel nGramLanguageModel = new NGramLanguageModel(stream);

      PerformanceMonitor perfMon = null;

      try (ObjectStream<String> lineStream = new PlainTextByLineStream(
              new SystemInputStreamFactory(), SystemInputStreamFactory.encoding())) {
        perfMon = new PerformanceMonitor("nglm");
        perfMon.start();
        String line;
        while ((line = lineStream.read()) != null) {
          double probability;
          String[] predicted;
          // TODO : use a Tokenizer here
          String[] tokens = line.split(" ");
          try {
            probability = nGramLanguageModel.calculateProbability(tokens);
            predicted = nGramLanguageModel.predictNextTokens(tokens);
          } catch (Exception e) {
            logger.error("Error for line: {}", line, e);
            continue;
          }

          logger.info("{} -> prob: {}, next: {}",
              Arrays.toString(tokens), probability, Arrays.toString(predicted));

          perfMon.incrementCounter();
        }
      } catch (IOException e) {
        CmdLineUtil.handleStdinIoError(e);
      }

      perfMon.stopAndPrintFinalResult();

    } catch (IOException e) {
      logger.error(e.getLocalizedMessage(), e);
    }
    // do nothing
  }

  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model";
  }
}
