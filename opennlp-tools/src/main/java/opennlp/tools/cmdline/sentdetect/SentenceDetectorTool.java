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

package opennlp.tools.cmdline.sentdetect;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParagraphStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * A sentence detector which uses a maxent model to predict the sentences.
 */
public final class SentenceDetectorTool extends BasicCmdLineTool {

  public String getShortDescription() {
    return "learnable sentence detector";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model < sentences";
  }

  /**
   * Perform sentence detection the input stream.
   *
   * A newline will be treated as a paragraph boundary.
   */
  public void run(String[] args) {

    if (args.length != 1) {
      System.out.println(getHelp());
    } else {

      SentenceModel model = new SentenceModelLoader().load(new File(args[0]));

      SentenceDetectorME sdetector = new SentenceDetectorME(model);

      PerformanceMonitor perfMon = new PerformanceMonitor(System.err, "sent");
      perfMon.start();

      try {
        ObjectStream<String> paraStream = new ParagraphStream(new PlainTextByLineStream(
            new SystemInputStreamFactory(), SystemInputStreamFactory.encoding()));

        String para;
        while ((para = paraStream.read()) != null) {

          String[] sents = sdetector.sentDetect(para);
          for (String sentence : sents) {
            System.out.println(sentence);
          }

          perfMon.incrementCounter(sents.length);

          System.out.println();
        }
      }
      catch (IOException e) {
        CmdLineUtil.handleStdinIoError(e);
      }

      perfMon.stopAndPrintFinalResult();
    }
  }
}
