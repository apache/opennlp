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

import opennlp.tools.cmdline.namefind.TokenNameFinder;
import opennlp.tools.cmdline.namefind.TokenNameFinderEvaluator;
import opennlp.tools.cmdline.namefind.TokenNameFinderTrainer;
import opennlp.tools.cmdline.parser.BuildModelUpdater;
import opennlp.tools.cmdline.parser.CheckModelUpdater;
import opennlp.tools.cmdline.parser.Parser;
import opennlp.tools.cmdline.parser.ParserTrainer;
import opennlp.tools.cmdline.parser.TaggerModelReplacer;
import opennlp.tools.cmdline.postag.POSTaggerEvaluator;
import opennlp.tools.cmdline.postag.POSTaggerTrainer;
import opennlp.tools.cmdline.sentdetect.SentenceDetector;
import opennlp.tools.cmdline.sentdetect.SentenceDetectorEvaluator;
import opennlp.tools.cmdline.sentdetect.SentenceDetectorTrainer;
import opennlp.tools.cmdline.sentdetect.SentenceDetectorCrossValidator;
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
    
    // Sentence detector
    tools.add(new SentenceDetector());
    tools.add(new SentenceDetectorTrainer());
    tools.add(new SentenceDetectorEvaluator());
    tools.add(new SentenceDetectorCrossValidator());
    
    // Name Finder
    tools.add(new TokenNameFinder());
    tools.add(new TokenNameFinderTrainer());
    tools.add(new TokenNameFinderEvaluator());
    
    // POS Tagger
    tools.add(new opennlp.tools.cmdline.postag.POSTagger());
    tools.add(new POSTaggerTrainer());
    tools.add(new POSTaggerEvaluator());
    
    // add evaluator
    // add cv validator
    
    // Chunker
    // ChunkerME, needs ChunkerModel and input must contain POS tags ...
    // ChunkerTrainer, on which material can we train? which format ?
    // how to evaluate ???
    
    // Parser
    tools.add(new Parser());
    tools.add(new ParserTrainer()); // trains everything
    tools.add(new BuildModelUpdater()); // re-trains  build model
    tools.add(new CheckModelUpdater()); // re-trains  build model
    tools.add(new TaggerModelReplacer());
    
    // Coref
    // Add util to use coref ...
    // training form corpus not part of 1.5
    
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
    
    if (tool == null) {
      usage();
      System.exit(1); 
    }
    else if (args.length > 1 && args[1].equals("help")) {
      System.out.println(tool.getHelp());
      System.exit(1);
    }
    
    tool.run(toolArguments);
  }
  
}
