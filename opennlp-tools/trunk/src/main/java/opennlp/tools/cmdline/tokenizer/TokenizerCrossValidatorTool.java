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

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenSampleStream;
import opennlp.tools.util.FMeasure;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class TokenizerCrossValidatorTool implements CmdLineTool {

  public String getName() {
    return "TokenizerCrossValidator";
  }
  
  public String getShortDescription() {
    return "10-fold cross validator for the learnable tokenizer";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " " + TrainingParameters.getParameterUsage() +
        " trainData\n" + 
        TrainingParameters.getDescription() + "\n"+
        "trainingData      training data used for cross validation";
  }

  public void run(String[] args) {
      if (args.length < 5) {
        System.out.println(getHelp());
        System.exit(1);
      }
      
      TrainingParameters parameters = new TrainingParameters(args);
      
      if(!parameters.isValid()) {
        System.out.println(getHelp());
        System.exit(1);
      }
      
      try {
      File trainingDataInFile = new File(args[args.length -1]);
      CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
      
      FileInputStream trainingDataIn = new FileInputStream(trainingDataInFile);
      ObjectStream<String> lineStream = new PlainTextByLineStream(trainingDataIn.getChannel(),
          parameters.getEncoding());
      ObjectStream<TokenSample> sampleStream = new TokenSampleStream(lineStream);
      
      opennlp.tools.tokenize.TokenizerCrossValidator validator =
          new opennlp.tools.tokenize.TokenizerCrossValidator(
          parameters.getLanguage(), parameters.isAlphaNumericOptimizationEnabled());
      
      validator.evaluate(sampleStream, 10);
      
      FMeasure result = validator.getFMeasure();
      
      System.out.println(result.toString());
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
