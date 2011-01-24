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

package opennlp.tools.cmdline.doccat;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParagraphStream;
import opennlp.tools.util.PlainTextByLineStream;

public class DoccatTool implements CmdLineTool {

  public String getName() {
    return "Doccat";
  }
  
  public String getShortDescription() {
    return "learnable document categorizer";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model < documents";
  }

  public void run(String[] args) {
    
    if (args.length != 1) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    DoccatModel model = new DoccatModelLoader().load(new File(args[0]));
    
    DocumentCategorizerME doccat = new DocumentCategorizerME(model);
    
    ObjectStream<String> documentStream = new ParagraphStream(
        new PlainTextByLineStream(new InputStreamReader(System.in)));
    
    PerformanceMonitor perfMon = new PerformanceMonitor(System.err, "doc");
    perfMon.start();
    
    try {
      String document;
      while ((document = documentStream.read()) != null) {
        double prob[] = doccat.categorize(document);
        String category = doccat.getBestCategory(prob);
        
        DocumentSample sample = new DocumentSample(category, document);
        System.out.println(sample.toString());
        
        perfMon.incrementCounter();
      }
    }
    catch (IOException e) {
      CmdLineUtil.handleStdinIoError(e);
    }
    
    perfMon.stopAndPrintFinalResult();
  }
}
