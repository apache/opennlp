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
import java.io.IOException;
import java.io.InputStreamReader;

import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.tokenize.Detokenizer;
import opennlp.tools.tokenize.Detokenizer.DetokenizationOperation;
import opennlp.tools.tokenize.DictionaryDetokenizer;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;

public final class DictionaryDetokenizerTool implements CmdLineTool {

  public String getName() {
    return "DictionaryDetokenizer";
  }

  public String getShortDescription() {
    return "";
  }

  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " detokenizerDictionary";
  }

  public static String detokenize(String tokens[], DetokenizationOperation operations[]) {
 
    if (tokens.length != operations.length)
      throw new IllegalArgumentException("tokens and operations array must have same length!");
    
    
    StringBuilder untokenizedString = new StringBuilder();
    
    for (int i = 0; i < tokens.length; i++) {
      
      // attach token to string buffer
      untokenizedString.append(tokens[i]);
      
      boolean isAppendSpace;
      
      // if this token is the last token do not attach a space
      if (i + 1 == operations.length) {
        isAppendSpace = false;
      }
      // if next token move left, no space after this token,
      // its safe to access next token
      else if (operations[i + 1].equals(DetokenizationOperation.MERGE_TO_LEFT)) {
        isAppendSpace = false;
      }
      // if this token is move right, no space 
      else if (operations[i].equals(DetokenizationOperation.MERGE_TO_RIGHT)) {
        isAppendSpace = false;
      }
      else {
        isAppendSpace = true;
      }
      
      if (isAppendSpace)
        untokenizedString.append(' ');
    }
    
    return untokenizedString.toString();
  }
  
  public void run(String[] args) {
    
    
    if (args.length != 1) {
      System.out.println(getHelp());
      throw new TerminateToolException(1);
    }
    
    Detokenizer detokenizer = new DictionaryDetokenizer(
        new DetokenizationDictionaryLoader().load(new File(args[0])));
    
    ObjectStream<String> tokenizedLineStream =
      new PlainTextByLineStream(new InputStreamReader(System.in));
    
    PerformanceMonitor perfMon = new PerformanceMonitor(System.err, "sent");
    perfMon.start();
    
    try {
      String tokenizedLine;
      while ((tokenizedLine = tokenizedLineStream.read()) != null) {
        
        // white space tokenize line
        String tokens[] = WhitespaceTokenizer.INSTANCE.tokenize(tokenizedLine);
        
        DetokenizationOperation operations[] = detokenizer.detokenize(tokens);
        
        System.out.println(detokenize(tokens, operations));
        
        perfMon.incrementCounter();
      }
    }
    catch (IOException e) {
      CmdLineUtil.handleStdinIoError(e);
    }
    
    perfMon.stopAndPrintFinalResult();
  }
}
