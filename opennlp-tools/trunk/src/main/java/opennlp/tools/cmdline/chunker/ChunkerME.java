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

package opennlp.tools.cmdline.chunker;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.Span;

public class ChunkerME implements CmdLineTool {

  public String getName() {
    return "ChunkerME";
  }
  
  public String getShortDescription() {
    return "";
  }
  
  public String getHelp() {
    return "";
  }

  public void run(String[] args) {
    if (args.length != 1) {
      System.out.println(getHelp());
      System.exit(1);
    }
    
    try {
      InputStream modelIn = new FileInputStream(args[0]);
      ChunkerModel model = new ChunkerModel(modelIn);
      modelIn.close();
      
      opennlp.tools.chunker.ChunkerME chunker = 
          new opennlp.tools.chunker.ChunkerME(model);
      
      ObjectStream<String> untokenizedLineStream =
        new PlainTextByLineStream(new InputStreamReader(System.in));
    
      String line;
      while((line = untokenizedLineStream.read()) != null) {
        String whitespaceTokenizerLine[] = WhitespaceTokenizer.INSTANCE.tokenize(line);
        
//        Span names[] = chunker.
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
