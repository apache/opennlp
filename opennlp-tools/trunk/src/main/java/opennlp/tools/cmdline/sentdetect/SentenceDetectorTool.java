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
import java.io.InputStreamReader;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParagraphStream;
import opennlp.tools.util.PlainTextByLineStream;

/**
 * A sentence detector which uses a maxent model to predict the sentences.
 */
public final class SentenceDetectorTool implements CmdLineTool {

  public String getName() {
    return "SentenceDetector";
  }
  
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
      throw new TerminateToolException(1);
    }

    SentenceModel model = new SentenceModelLoader().load(new File(args[0]));
    
    SentenceDetectorME sdetector = new SentenceDetectorME(model);

    ObjectStream<String> lineStream =
      new ParagraphStream(new PlainTextByLineStream(new InputStreamReader(System.in)));
    
    PerformanceMonitor perfMon = new PerformanceMonitor(System.err, "sent");
    perfMon.start();
    
    try {
      while (true) {
        String para = lineStream.read();
        
        // The last paragraph in the input might not
        // be terminated well with a new line at the end.
        
        if ((para != null) && (para.length() > 0)) {
          // process the paragraph data here
          String[] sents = sdetector.sentDetect(para);
          for (String sentence : sents) {
            System.out.println(sentence);
          }

          perfMon.incrementCounter(sents.length);
        }
        if (para == null)
          break;
        System.out.println();
      }
    } 
    catch (IOException e) {
      CmdLineUtil.handleStdinIoError(e);
    }
    
    perfMon.stopAndPrintFinalResult();
  }
}
