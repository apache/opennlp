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

import opennlp.model.AbstractModel;
import opennlp.tools.cmdline.BasicTrainingParameters;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParseSampleStream;
import opennlp.tools.parser.ParserEventTypeEnum;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.parser.chunking.Parser;
import opennlp.tools.parser.chunking.ParserEventStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public class BuildModelUpdater implements CmdLineTool {

  public String getName() {
    return "BuildModelUpdater";
  }
  
  public String getShortDescription() {
    return "trains a build model and updates it in an existing parser model";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " training.file parser.model";
  }
  
  public void run(String[] args) {
    if (args.length < 6) {
      System.out.println(getHelp());
      System.exit(1);
    }
    
    BasicTrainingParameters parameters = new BasicTrainingParameters(args);
    
    try {
      // Load model to be updated
      File modelFile = new File(args[args.length - 1]);
      CmdLineUtil.checkInputFile("Parser Model", modelFile);
  
      // TODO: close the stream
      ParserModel parserModel = ParserModel.create(new FileInputStream(modelFile));
      
      File trainingDataInFile = new File(args[args.length - 2]);
      CmdLineUtil.checkInputFile("Training Data", trainingDataInFile);
      
      FileInputStream trainingDataIn = new FileInputStream(trainingDataInFile);
      ObjectStream<Parse> parseSamples = new ParseSampleStream(new PlainTextByLineStream(trainingDataIn.getChannel(),
          parameters.getEncoding()));
      
      // Create dictionary
      System.out.print("Building dictionary ...");
      Dictionary mdict = Parser.
          buildDictionary(parseSamples, parserModel.getHeadRules(), parameters.getCutoff());
      System.out.println("done");
      
      parseSamples.reset();
      
      // Training build
      System.out.println("Training builder");
      opennlp.model.EventStream bes = new ParserEventStream(parseSamples, parserModel.getHeadRules(), ParserEventTypeEnum.BUILD, mdict);
      AbstractModel buildModel = Parser.train(bes, parameters.getNumberOfIterations(), 
          parameters.getCutoff());
      
      parseSamples.close();
      
      ParserModel updatedParserModel = parserModel.updateBuildModel(buildModel);
      
      // TODO: Should we overwrite the model file, really ?
      OutputStream modelOut = new FileOutputStream(modelFile);
      updatedParserModel.serialize(modelOut);
      modelOut.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
