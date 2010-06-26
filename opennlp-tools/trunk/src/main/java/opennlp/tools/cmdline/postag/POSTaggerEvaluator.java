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
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.postag.POSEvaluator;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.WordTagSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class POSTaggerEvaluator implements CmdLineTool {

  public String getName() {
    return "POSTaggerEvaluator";
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
      
      File testData = new File(args[args.length - 1]);
      CmdLineUtil.checkInputFile("Test data", testData);
      
      Charset encoding = CmdLineUtil.getEncodingParameter(args);
      
      if (encoding == null) {
        System.out.println(getHelp());
        System.exit(1);
      }
      
      POSModel model = POSTagger.loadModel(new File(args[args.length - 2]));
      
      POSEvaluator evaluator = 
          new POSEvaluator(new opennlp.tools.postag.POSTaggerME(model));
      
      System.out.print("Evaluating ... ");
      ObjectStream<String> sampleStringStream = new PlainTextByLineStream(
          new InputStreamReader(new FileInputStream(testData), encoding));
      
      evaluator.evaluate(new WordTagSampleStream(sampleStringStream));
      sampleStringStream.close();
      System.out.println("done");
      
      System.out.println();
      
      System.out.println("Accuracy: " + evaluator.getWordAccuracy());
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
