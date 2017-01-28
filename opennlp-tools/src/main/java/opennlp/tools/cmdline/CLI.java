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
import java.util.Set;

import opennlp.tools.cmdline.chunker.ChunkerConverterTool;
import opennlp.tools.cmdline.chunker.ChunkerCrossValidatorTool;
import opennlp.tools.cmdline.chunker.ChunkerEvaluatorTool;
import opennlp.tools.cmdline.chunker.ChunkerMETool;
import opennlp.tools.cmdline.chunker.ChunkerTrainerTool;
import opennlp.tools.cmdline.dictionary.DictionaryBuilderTool;
import opennlp.tools.cmdline.doccat.DoccatConverterTool;
import opennlp.tools.cmdline.doccat.DoccatCrossValidatorTool;
import opennlp.tools.cmdline.doccat.DoccatEvaluatorTool;
import opennlp.tools.cmdline.doccat.DoccatTool;
import opennlp.tools.cmdline.doccat.DoccatTrainerTool;
import opennlp.tools.cmdline.entitylinker.EntityLinkerTool;
import opennlp.tools.cmdline.languagemodel.NGramLanguageModelTool;
import opennlp.tools.cmdline.lemmatizer.LemmatizerEvaluatorTool;
import opennlp.tools.cmdline.lemmatizer.LemmatizerMETool;
import opennlp.tools.cmdline.lemmatizer.LemmatizerTrainerTool;
import opennlp.tools.cmdline.namefind.CensusDictionaryCreatorTool;
import opennlp.tools.cmdline.namefind.TokenNameFinderConverterTool;
import opennlp.tools.cmdline.namefind.TokenNameFinderCrossValidatorTool;
import opennlp.tools.cmdline.namefind.TokenNameFinderEvaluatorTool;
import opennlp.tools.cmdline.namefind.TokenNameFinderTool;
import opennlp.tools.cmdline.namefind.TokenNameFinderTrainerTool;
import opennlp.tools.cmdline.parser.BuildModelUpdaterTool;
import opennlp.tools.cmdline.parser.CheckModelUpdaterTool;
import opennlp.tools.cmdline.parser.ParserConverterTool;
import opennlp.tools.cmdline.parser.ParserEvaluatorTool;
import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.cmdline.parser.ParserTrainerTool;
import opennlp.tools.cmdline.parser.TaggerModelReplacerTool;
import opennlp.tools.cmdline.postag.POSTaggerConverterTool;
import opennlp.tools.cmdline.postag.POSTaggerCrossValidatorTool;
import opennlp.tools.cmdline.postag.POSTaggerEvaluatorTool;
import opennlp.tools.cmdline.postag.POSTaggerTrainerTool;
import opennlp.tools.cmdline.sentdetect.SentenceDetectorConverterTool;
import opennlp.tools.cmdline.sentdetect.SentenceDetectorCrossValidatorTool;
import opennlp.tools.cmdline.sentdetect.SentenceDetectorEvaluatorTool;
import opennlp.tools.cmdline.sentdetect.SentenceDetectorTool;
import opennlp.tools.cmdline.sentdetect.SentenceDetectorTrainerTool;
import opennlp.tools.cmdline.tokenizer.DictionaryDetokenizerTool;
import opennlp.tools.cmdline.tokenizer.SimpleTokenizerTool;
import opennlp.tools.cmdline.tokenizer.TokenizerConverterTool;
import opennlp.tools.cmdline.tokenizer.TokenizerCrossValidatorTool;
import opennlp.tools.cmdline.tokenizer.TokenizerMEEvaluatorTool;
import opennlp.tools.cmdline.tokenizer.TokenizerMETool;
import opennlp.tools.cmdline.tokenizer.TokenizerTrainerTool;
import opennlp.tools.util.Version;

public final class CLI {

  public static final String CMD = "opennlp";

  private static Map<String, CmdLineTool> toolLookupMap;

  static {
    toolLookupMap = new LinkedHashMap<>();

    List<CmdLineTool> tools = new LinkedList<>();

    // Document Categorizer
    tools.add(new DoccatTool());
    tools.add(new DoccatTrainerTool());
    tools.add(new DoccatEvaluatorTool());
    tools.add(new DoccatCrossValidatorTool());
    tools.add(new DoccatConverterTool());

    // Dictionary Builder
    tools.add(new DictionaryBuilderTool());

    // Tokenizer
    tools.add(new SimpleTokenizerTool());
    tools.add(new TokenizerMETool());
    tools.add(new TokenizerTrainerTool());
    tools.add(new TokenizerMEEvaluatorTool());
    tools.add(new TokenizerCrossValidatorTool());
    tools.add(new TokenizerConverterTool());
    tools.add(new DictionaryDetokenizerTool());

    // Sentence detector
    tools.add(new SentenceDetectorTool());
    tools.add(new SentenceDetectorTrainerTool());
    tools.add(new SentenceDetectorEvaluatorTool());
    tools.add(new SentenceDetectorCrossValidatorTool());
    tools.add(new SentenceDetectorConverterTool());

    // Name Finder
    tools.add(new TokenNameFinderTool());
    tools.add(new TokenNameFinderTrainerTool());
    tools.add(new TokenNameFinderEvaluatorTool());
    tools.add(new TokenNameFinderCrossValidatorTool());
    tools.add(new TokenNameFinderConverterTool());
    tools.add(new CensusDictionaryCreatorTool());


    // POS Tagger
    tools.add(new opennlp.tools.cmdline.postag.POSTaggerTool());
    tools.add(new POSTaggerTrainerTool());
    tools.add(new POSTaggerEvaluatorTool());
    tools.add(new POSTaggerCrossValidatorTool());
    tools.add(new POSTaggerConverterTool());

    //Lemmatizer
    tools.add(new LemmatizerMETool());
    tools.add(new LemmatizerTrainerTool());
    tools.add(new LemmatizerEvaluatorTool());

    // Chunker
    tools.add(new ChunkerMETool());
    tools.add(new ChunkerTrainerTool());
    tools.add(new ChunkerEvaluatorTool());
    tools.add(new ChunkerCrossValidatorTool());
    tools.add(new ChunkerConverterTool());

    // Parser
    tools.add(new ParserTool());
    tools.add(new ParserTrainerTool()); // trains everything
    tools.add(new ParserEvaluatorTool());
    tools.add(new ParserConverterTool()); // trains everything
    tools.add(new BuildModelUpdaterTool()); // re-trains  build model
    tools.add(new CheckModelUpdaterTool()); // re-trains  build model
    tools.add(new TaggerModelReplacerTool());

    // Entity Linker
    tools.add(new EntityLinkerTool());

    // Language Model
    tools.add(new NGramLanguageModelTool());

    for (CmdLineTool tool : tools) {
      toolLookupMap.put(tool.getName(), tool);
    }

    toolLookupMap = Collections.unmodifiableMap(toolLookupMap);
  }

  /**
   * @return a set which contains all tool names
   */
  public static Set<String> getToolNames() {
    return toolLookupMap.keySet();
  }

  /**
   * @return a read only map with tool names and instances
   */
  public static Map<String, CmdLineTool> getToolLookupMap() {
    return toolLookupMap;
  }

  private static void usage() {
    System.out.print("OpenNLP " + Version.currentVersion().toString() + ". ");
    System.out.println("Usage: " + CMD + " TOOL");
    System.out.println("where TOOL is one of:");

    // distance of tool name from line start
    int numberOfSpaces = -1;
    for (String toolName : toolLookupMap.keySet()) {
      if (toolName.length() > numberOfSpaces) {
        numberOfSpaces = toolName.length();
      }
    }
    numberOfSpaces = numberOfSpaces + 4;

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
      System.exit(0);
    }

    final long startTime = System.currentTimeMillis();
    String toolArguments[] = new String[args.length - 1];
    System.arraycopy(args, 1, toolArguments, 0, toolArguments.length);

    String toolName = args[0];

    //check for format
    String formatName = StreamFactoryRegistry.DEFAULT_FORMAT;
    int idx = toolName.indexOf(".");
    if (-1 < idx) {
      formatName = toolName.substring(idx + 1);
      toolName = toolName.substring(0, idx);
    }
    CmdLineTool tool = toolLookupMap.get(toolName);

    try {
      if (null == tool) {
        throw new TerminateToolException(1, "Tool " + toolName + " is not found.");
      }

      if ((0 == toolArguments.length && tool.hasParams()) ||
          0 < toolArguments.length && "help".equals(toolArguments[0])) {
        if (tool instanceof TypedCmdLineTool) {
          System.out.println(((TypedCmdLineTool) tool).getHelp(formatName));
        } else if (tool instanceof BasicCmdLineTool) {
          System.out.println(tool.getHelp());
        }

        System.exit(0);
      }

      if (tool instanceof TypedCmdLineTool) {
        ((TypedCmdLineTool) tool).run(formatName, toolArguments);
      } else if (tool instanceof BasicCmdLineTool) {
        if (-1 == idx) {
          ((BasicCmdLineTool) tool).run(toolArguments);
        } else {
          throw new TerminateToolException(1, "Tool " + toolName + " does not support formats.");
        }
      } else {
        throw new TerminateToolException(1, "Tool " + toolName + " is not supported.");
      }
    }
    catch (TerminateToolException e) {

      if (e.getMessage() != null) {
        System.err.println(e.getMessage());
      }

      if (e.getCause() != null) {
        System.err.println(e.getCause().getMessage());
        e.getCause().printStackTrace(System.err);
      }

      System.exit(e.getCode());
    }

    final long endTime = System.currentTimeMillis();
    System.out.format("Execution time: %.3f seconds\n", (endTime - startTime) / 1000.0);
  }
}
