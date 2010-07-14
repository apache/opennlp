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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.ObjectStreamException;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public final class TokenNameFinderTool implements CmdLineTool {

  public String getName() {
    return "TokenNameFinder";
  }
  
  public String getShortDescription() {
    return "learnable name finder";
  }
  
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " model1 model2 ... modelN < sentences";
  }
  
  static TokenNameFinderModel loadModel(File modelFile) {
    
    CmdLineUtil.checkInputFile("Token Name Finder model", modelFile);

    System.err.print("Loading model " + modelFile.getName() + " ... ");
    
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
    
    if (args.length == 0) {
      System.out.println(getHelp());
      System.exit(1);
    }
    
    
    NameFinderME nameFinders[] = new NameFinderME[args.length];
    
    for (int i = 0; i < nameFinders.length; i++) {
      TokenNameFinderModel model = loadModel(new File(args[i]));
      nameFinders[i] = new NameFinderME(model);
    }
    
    ObjectStream<String> untokenizedLineStream =
        new PlainTextByLineStream(new InputStreamReader(System.in));
    
    try {
      String line;
      while((line = untokenizedLineStream.read()) != null) {
        String whitespaceTokenizerLine[] = WhitespaceTokenizer.INSTANCE.tokenize(line);
        
        // A new line indicates a new document,
        // adaptive data must be cleared for a new document
        
        if (whitespaceTokenizerLine.length == 0) {
          for (int i = 0; i < nameFinders.length; i++)  
            nameFinders[i].clearAdaptiveData();
        }
        
        List<Span> names = new ArrayList<Span>();
        
        for (TokenNameFinder nameFinder : nameFinders) {
          Collections.addAll(names, nameFinder.find(whitespaceTokenizerLine));
        }
        
        // Simple way to drop intersecting spans, otherwise the
        // NameSample is invalid
        Span reducedNames[] = NameFinderME.dropOverlappingSpans(
            names.toArray(new Span[names.size()]));
        
        NameSample nameSample = new NameSample(whitespaceTokenizerLine,
            reducedNames, false);
        
        System.out.println(nameSample.toString());
      }
    }
    catch (ObjectStreamException e) {
      CmdLineUtil.handleStdinIoError(e);
    }
  }
}
