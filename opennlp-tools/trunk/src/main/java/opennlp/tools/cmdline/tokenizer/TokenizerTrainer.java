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
import java.io.FileOutputStream;
import java.io.OutputStream;

import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.tokenize.TokenSample;
import opennlp.tools.tokenize.TokenSampleStream;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class TokenizerTrainer implements CmdLineTool {

  private static void usage() {
    System.err.println("Usage: TokenizerCrossValidator " + TrainingParameters.getParameterUsage() +
        " trainData model");
    System.err.println(TrainingParameters.getDescription());
    System.err.println("trainingData      training data used for cross validation");
    System.err.println("model             output file for the created tokenizer model");
    System.exit(1);
  }
  
  public String getName() {
    return "TokenizerTrainer";
  }
  
  public String getShortDescription() {
    return "trainer for the learnable tokenizer";
  }
  
  public String getHelp() {
    return "";
  }


  public void run(String[] args) {
    try {
      if (args.length < 6) {
        usage();
      }
      
      TrainingParameters parameters = TrainingParameters.parse(args);
      
      if(parameters == null) {
        usage();
      }
      
      File trainingDataInFile = new File(args[args.length - 2]);
      CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
      
      FileInputStream trainingDataIn = new FileInputStream(trainingDataInFile);
      ObjectStream<String> lineStream = new PlainTextByLineStream(trainingDataIn.getChannel(),
          parameters.getEncoding());
      ObjectStream<TokenSample> sampleStream = new TokenSampleStream(lineStream);
      
      TokenizerModel model = opennlp.tools.tokenize.TokenizerME.train(parameters.getLanguage(), sampleStream, 
          parameters.isAlphaNumericOptimizationEnabled());
      
      sampleStream.close();
      
      File modelOutFile = new File(args[args.length - 1]);
      OutputStream modelOut = new FileOutputStream(modelOutFile);
      model.serialize(modelOut);
      modelOut.close();
      
      System.out.println("Saving tokenizer model as: " + modelOutFile.getAbsolutePath());
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
