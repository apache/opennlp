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


package opennlp.tools.cmdline.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

import opennlp.tools.cmdline.BasicTrainingParameters;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.parser.HeadRules;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParseSampleStream;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class ParserTrainer implements CmdLineTool {

  public String getName() {
    return "ParserTrainer";
  }
  
  public String getShortDescription() {
    return "";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " head_rules trainingData model";
  }

  public void run(String[] args) {
    
    if (args.length < 7) {
      System.out.println(getHelp());
      System.exit(1);
    }

    BasicTrainingParameters parameters = new BasicTrainingParameters(args);
    
    if(!parameters.isValid()) {
      System.out.println(getHelp());
      System.exit(1);
    } 
    
    File trainingDataInFile = new File(args[args.length - 2]);
    CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
    
    try {
      FileInputStream trainingDataIn = new FileInputStream(trainingDataInFile);
      ObjectStream<String> lineStream = new PlainTextByLineStream(trainingDataIn.getChannel(),
          parameters.getEncoding());
      
      ObjectStream<Parse> sampleStream = new ParseSampleStream(lineStream);
      
      HeadRules rules = new opennlp.tools.parser.lang.en.HeadRules(args[args.length - 3]);
      
      ParserModel model = Parser.train(sampleStream, rules, parameters.getNumberOfIterations(), 
          parameters.getCutoff());
      
      sampleStream.close();
      
      File modelOutFile = new File(args[args.length - 1]);
      OutputStream modelOut = new FileOutputStream(modelOutFile);
      model.serialize(modelOut);
      modelOut.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
