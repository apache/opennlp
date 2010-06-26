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

package opennlp.tools.cmdline.namefind;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.PerformanceMonitor;
import opennlp.tools.util.PlainTextByLineStream;

public class TokenNameFinderEvaluator implements CmdLineTool {

  public String getName() {
    return "TokenNameFinderEvaluator";
  }
  
  public String getShortDescription() {
    return "";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " -encoding charset model testData";
  }

  public void run(String[] args) {
    try {
      if (args.length != 4) {
        System.out.println(getHelp());
        System.exit(1);
      }
      
      File testData = new File(args[3]);
      CmdLineUtil.checkInputFile("Test data", testData);
      
      Charset encoding = CmdLineUtil.getEncodingParameter(args);
      
      if (encoding == null) {
        System.out.println(getHelp());
        System.exit(1);
      }
      
      TokenNameFinderModel model = TokenNameFinder.loadModel(new File(args[2]));
      
      opennlp.tools.namefind.TokenNameFinderEvaluator evaluator = 
          new opennlp.tools.namefind.TokenNameFinderEvaluator(new NameFinderME(model));
      
      ObjectStream<String> sampleStringStream = new PlainTextByLineStream(
          new InputStreamReader(new FileInputStream(testData), encoding));
      
      final ObjectStream<NameSample> sampleStream = 
          new NameSampleDataStream(sampleStringStream);
      
      final PerformanceMonitor monitor = new PerformanceMonitor("sent");
      
      ObjectStream<NameSample> measuredSampleStream = new ObjectStream<NameSample>() {

        public NameSample read() throws ObjectStreamException {
          monitor.incrementCounter();
          return sampleStream.read();
        }
        
        public void reset() throws ObjectStreamException {
          sampleStream.reset();
        }
        
        public void close() throws ObjectStreamException {
          sampleStream.close();
        }
      };
      
      monitor.startPrinter();
      
      evaluator.evaluate(measuredSampleStream);
      
      monitor.stopPrinterAndPrintFinalResult();
      
      sampleStringStream.close();
      
      System.out.println();
      
      System.out.println(evaluator.getFMeasure());
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
