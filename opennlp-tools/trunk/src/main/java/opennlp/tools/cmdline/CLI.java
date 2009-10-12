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


package opennlp.tools.cmdline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import opennlp.tools.cmdline.parser.BuildModelUpdater;
import opennlp.tools.cmdline.sentdetect.SentenceDetector;
import opennlp.tools.cmdline.tokenizer.SimpleTokenizer;
import opennlp.tools.cmdline.tokenizer.TokenizerCrossValidator;
import opennlp.tools.cmdline.tokenizer.TokenizerME;
import opennlp.tools.cmdline.tokenizer.TokenizerMEEvaluator;
import opennlp.tools.cmdline.tokenizer.TokenizerTrainer;

public class CLI {
  
  public static final String CMD = "opennlp";
  
  private static Map<String, CmdLineTool> toolLookupMap;
  
  static {
    toolLookupMap = new LinkedHashMap<String, CmdLineTool>();
    
    List<CmdLineTool> tools = new LinkedList<CmdLineTool>();
    
    // Tokenizer
    tools.add(new SimpleTokenizer());
    tools.add(new TokenizerME());
    tools.add(new TokenizerTrainer());
    tools.add(new TokenizerMEEvaluator());
    tools.add(new TokenizerCrossValidator());
    tools.add(new SentenceDetector());
    
    // Sentence detector
    // Name Finder
    // POS Tagger
    // Chunker
    // Parser
//    tools.add(new BuildModelUpdater());
    // Coref
    
    for (CmdLineTool tool : tools) {
      toolLookupMap.put(tool.getName(), tool);
    }
    
    toolLookupMap = Collections.unmodifiableMap(toolLookupMap);
  }
  
  private static void usage() {
    System.out.println("Usage: " + CMD + " TOOL");
    System.out.println("where TOOL is one of:");
    
    // distance of tool name from line start
    int numberOfSpaces = 25;
    
    for (CmdLineTool tool : toolLookupMap.values()) {
      
      System.out.print("  " + tool.getName());
      
      for (int i = 0; i < Math.abs(tool.getName().length() - numberOfSpaces); i++) {
        System.out.print(" ");
      }
      
      System.out.println(tool.getShortDescription());
    }
    
    System.out.println("All tools print help when invoked with help parameter");
    System.out.println("Example: opennlp SimpleTokenizer help");
  }
  
  public static void main(String[] args) {
    
    if (args.length == 0) {
      usage();
      System.exit(1);
    }
    
    String toolArguments[] = new String[args.length -1];
    System.arraycopy(args, 1, toolArguments, 0, toolArguments.length);
    
    CmdLineTool tool = toolLookupMap.get(args[0]);
    
    if (args.length > 1 && args[1].equals("help")) {
      System.out.println(tool.getHelp());
      System.exit(1);
    }
    
    tool.run(toolArguments);
  }
  
}
