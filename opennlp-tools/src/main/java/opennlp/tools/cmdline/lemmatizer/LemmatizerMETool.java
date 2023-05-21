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

package opennlp.tools.cmdline.lemmatizer;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.lemmatizer.LemmaSample;
import opennlp.tools.lemmatizer.LemmatizerME;
import opennlp.tools.lemmatizer.LemmatizerModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class LemmatizerMETool extends BasicCmdLineTool {

  private static final Logger logger = LoggerFactory.getLogger(LemmatizerMETool.class);

  public String getShortDescription() {
    return "Learnable lemmatizer";
  }

  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model < sentences";
  }

  @Override
  public void run(String[] args) {
    if (args.length != 1) {
      logger.info(getHelp());
    } else {
      LemmatizerModel model = new LemmatizerModelLoader().load(new File(args[0]));
      LemmatizerME lemmatizer = new LemmatizerME(model);

      PerformanceMonitor perfMon = null;

      try (ObjectStream<String> lineStream = new PlainTextByLineStream(
              new SystemInputStreamFactory(), SystemInputStreamFactory.encoding())) {

        perfMon = new PerformanceMonitor("sent");
        perfMon.start();
        String line;
        while ((line = lineStream.read()) != null) {

          POSSample posSample;
          try {
            posSample = POSSample.parse(line);
          } catch (InvalidFormatException e) {
            logger.warn("Invalid format: {}", line);
            continue;
          }

          String[] lemmas = lemmatizer.lemmatize(posSample.getSentence(),
              posSample.getTags());

          logger.info(new LemmaSample(posSample.getSentence(),
              posSample.getTags(), lemmas).toString());

          perfMon.incrementCounter();
        }
      } catch (IOException e) {
        CmdLineUtil.handleStdinIoError(e);
      }

      perfMon.stopAndPrintFinalResult();
    }
  }
}
