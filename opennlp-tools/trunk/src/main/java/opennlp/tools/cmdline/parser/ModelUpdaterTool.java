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

import opennlp.tools.cmdline.BasicTrainingParameters;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.util.ObjectStream;

/** 
 * Abstract base class for tools which update the parser model.
 */
abstract class ModelUpdaterTool implements CmdLineTool {

  protected abstract ParserModel trainAndUpdate(ParserModel originalModel,
      ObjectStream<Parse> parseSamples, BasicTrainingParameters parameters);

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " training.file parser.model";
  }
  
  public final void run(String[] args) {

    if (args.length < 6) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    BasicTrainingParameters parameters = new BasicTrainingParameters(args);
    
    // Load model to be updated
    File modelFile = new File(args[args.length - 1]);
    ParserModel originalParserModel = opennlp.tools.cmdline.parser.ParserTool
        .loadModel(modelFile);

    ObjectStream<Parse> parseSamples = ParserTrainerTool.openTrainingData(new File(args[args.length - 2]), 
        parameters.getEncoding());
    
    ParserModel updatedParserModel = trainAndUpdate(originalParserModel,
        parseSamples, parameters);
    
    CmdLineUtil.writeModel("parser", modelFile, updatedParserModel);
  }
}
