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

package opennlp.tools.cmdline.tokenizer;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.tokenize.Detokenizer;
import opennlp.tools.tokenize.DictionaryDetokenizer;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public final class DictionaryDetokenizerTool extends BasicCmdLineTool {

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " detokenizerDictionary";
  }

  public void run(String[] args) {


    if (args.length != 1) {
      System.out.println(getHelp());
    } else {
      try {
        Detokenizer detokenizer = new DictionaryDetokenizer(
            new DetokenizationDictionaryLoader().load(new File(args[0])));

        ObjectStream<String> tokenizedLineStream =
            new PlainTextByLineStream(new SystemInputStreamFactory(), SystemInputStreamFactory.encoding());

        PerformanceMonitor perfMon = new PerformanceMonitor(System.err, "sent");
        perfMon.start();


        String tokenizedLine;
        while ((tokenizedLine = tokenizedLineStream.read()) != null) {

          // white space tokenize line
          String tokens[] = WhitespaceTokenizer.INSTANCE.tokenize(tokenizedLine);

          System.out.println(detokenizer.detokenize(tokens, null));

          perfMon.incrementCounter();
        }
        perfMon.stopAndPrintFinalResult();
      }
      catch (IOException e) {
        CmdLineUtil.handleStdinIoError(e);
      }


    }
  }
}
