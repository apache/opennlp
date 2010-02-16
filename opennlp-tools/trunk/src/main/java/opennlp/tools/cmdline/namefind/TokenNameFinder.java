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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public class TokenNameFinder implements CmdLineTool {

  public String getName() {
    return "TokenNameFinder";
  }
  
  public String getShortDescription() {
    return "";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model < sentences";
  }
  
  static TokenNameFinderModel loadModel(File modelFile) {
    
    CmdLineUtil.checkInputFile("Token Name Finder model", modelFile);

    System.err.print("Loading model ... ");
    
    InputStream modelIn = CmdLineUtil.openInFile(modelFile);
    
    TokenNameFinderModel model;
    try {
      model = new TokenNameFinderModel(modelIn);
      modelIn.close();
    }
    catch (IOException e) {
      System.err.println("failed");
      System.err.println("IO error while loading model: " + e.getMessage());
      System.exit(-1);
      return null;
    }
    catch (InvalidFormatException e) {
      System.err.println("failed");
      System.err.println("Model has invalid format: " + e.getMessage());
      System.exit(-1);
      return null;
    }
    
    System.err.println("done");
    
    return model;
  }
  
  public void run(String[] args) {
    
    if (args.length != 1) {
      System.out.println(getHelp());
      System.exit(1);
    }
    
    TokenNameFinderModel model = loadModel(new File(args[0]));

    NameFinderME nameFinder = new NameFinderME(model);
    
    ObjectStream<String> untokenizedLineStream =
        new PlainTextByLineStream(new InputStreamReader(System.in));
    
    try {
      String line;
      while((line = untokenizedLineStream.read()) != null) {
        String whitespaceTokenizerLine[] = WhitespaceTokenizer.INSTANCE.tokenize(line);
        
        Span names[] = nameFinder.find(whitespaceTokenizerLine);
        
        NameSample nameSample = new NameSample(whitespaceTokenizerLine, names, false);
        
        System.out.println(nameSample.toString());
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
