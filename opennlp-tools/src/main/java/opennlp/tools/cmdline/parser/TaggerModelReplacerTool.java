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

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.postag.POSModelLoader;
import opennlp.tools.parser.ParserModel;
import opennlp.tools.postag.POSModel;

// user should train with the POS tool
public final class TaggerModelReplacerTool extends BasicCmdLineTool {

  public String getShortDescription() {
    return "replaces the tagger model in a parser model";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " parser.model tagger.model";
  }

  public void run(String[] args) {

    if (args.length != 2) {
      System.out.println(getHelp());
    } else {

      File parserModelInFile = new File(args[0]);
      ParserModel parserModel = new ParserModelLoader().load(parserModelInFile);

      File taggerModelInFile = new File(args[1]);
      POSModel taggerModel = new POSModelLoader().load(taggerModelInFile);

      ParserModel updatedParserModel = parserModel.updateTaggerModel(taggerModel);

      CmdLineUtil.writeModel("parser", parserModelInFile, updatedParserModel);
    }
  }
}
