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

package opennlp.tools.cmdline.langdetect;

import java.io.File;
import java.io.IOException;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.SystemInputStreamFactory;
import opennlp.tools.langdetect.Language;
import opennlp.tools.langdetect.LanguageDetector;
import opennlp.tools.langdetect.LanguageDetectorME;
import opennlp.tools.langdetect.LanguageDetectorModel;
import opennlp.tools.langdetect.LanguageSample;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ParagraphStream;
import opennlp.tools.util.PlainTextByLineStream;

public class LanguageDetectorTool extends BasicCmdLineTool {

  @Override
  public String getShortDescription() {
    return "learned language detector";
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

      LanguageDetectorModel model = new LanguageDetectorModelLoader().load(new File(args[0]));

      LanguageDetector langDetectME = new LanguageDetectorME(model);

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

          Language lang = langDetectME.predictLanguage(document);

          LanguageSample sample = new LanguageSample(lang, document);
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
