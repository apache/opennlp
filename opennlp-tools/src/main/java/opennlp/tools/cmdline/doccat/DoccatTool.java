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

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParagraphStream;
import opennlp.tools.util.PlainTextByLineStream;

public class DoccatTool extends BasicCmdLineTool {

  @Override
  public String getShortDescription() {
    return "learnable document categorizer";
  }

  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model < documents";
  }

  @Override
  public void run(String[] args) {

    if (0 == args.length) {
      System.out.println(getHelp());
    } else {

      DoccatModel model = new DoccatModelLoader().load(new File(args[0]));

      DocumentCategorizerME doccat = new DocumentCategorizerME(model);

      /*
       * moved initialization to the try block to catch new IOException
       */
      ObjectStream<String> documentStream;

      PerformanceMonitor perfMon = new PerformanceMonitor(System.err, "doc");
      perfMon.start();

      try {
        documentStream = new ParagraphStream(new PlainTextByLineStream(
            new SystemInputStreamFactory(), SystemInputStreamFactory.encoding()));
        String document;
        while ((document = documentStream.read()) != null) {
          String[] tokens = model.getFactory().getTokenizer().tokenize(document);

          double prob[] = doccat.categorize(tokens);
          String category = doccat.getBestCategory(prob);

          DocumentSample sample = new DocumentSample(category, tokens);
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
