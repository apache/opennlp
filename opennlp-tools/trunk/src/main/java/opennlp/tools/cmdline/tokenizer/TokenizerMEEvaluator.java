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
import java.io.FileInputStream;
import java.io.InputStreamReader;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.tokenize.TokenSampleStream;
import opennlp.tools.tokenize.TokenizerEvaluator;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class TokenizerMEEvaluator implements CmdLineTool {

  public String getName() {
    return "TokenizerMEEvaluator";
  }
  
  public String getShortDescription() {
    return "evaluator for the learnable tokenizer";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + "-encoding charset model testData";
  }

  public void run(String[] args) {
    try {
      if (args.length != 2) {
        System.out.println(getHelp());
        System.exit(1);
      }
      
      File testData = new File(args[1]);
      CmdLineUtil.checkInputFile("Test data", testData);
      
      String encoding = CmdLineUtil.getEncodingParameter(args);
      
      if (encoding == null) {
        System.out.println(getHelp());
        System.exit(1);
      }
      
      TokenizerModel model = TokenizerME.loadModel(new File(args[0]));
      
      TokenizerEvaluator evaluator = 
          new TokenizerEvaluator(new opennlp.tools.tokenize.TokenizerME(model));
      
      System.out.print("Evaluating ... ");
      ObjectStream<String> sampleStringStream = new PlainTextByLineStream(
          new InputStreamReader(new FileInputStream(testData), encoding));
      
      evaluator.evaluate(new TokenSampleStream(sampleStringStream));
      sampleStringStream.close();
      System.out.println("done");
      
      System.out.println();
      
      System.out.println(evaluator.getFMeasure());
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
