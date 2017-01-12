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

package opennlp.tools.cmdline.postag;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSSample;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public final class POSTaggerTool extends BasicCmdLineTool {

  public String getShortDescription() {
    return "learnable part of speech tagger";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model < sentences";
  }

  public void run(String[] args) {

    if (args.length != 1) {
      System.out.println(getHelp());
    } else {

      POSModel model = new POSModelLoader().load(new File(args[0]));

      POSTaggerME tagger = new POSTaggerME(model);

      ObjectStream<String> lineStream;
      PerformanceMonitor perfMon = null;

      try {
        lineStream =
            new PlainTextByLineStream(new SystemInputStreamFactory(), SystemInputStreamFactory.encoding());
        perfMon = new PerformanceMonitor(System.err, "sent");
        perfMon.start();
        String line;
        while ((line = lineStream.read()) != null) {

          String whitespaceTokenizerLine[] = WhitespaceTokenizer.INSTANCE.tokenize(line);
          String[] tags = tagger.tag(whitespaceTokenizerLine);

          POSSample sample = new POSSample(whitespaceTokenizerLine, tags);
          System.out.println(sample.toString());

          perfMon.incrementCounter();
        }
      } catch (IOException e) {
        CmdLineUtil.handleStdinIoError(e);
      }

      perfMon.stopAndPrintFinalResult();
    }
  }
}
