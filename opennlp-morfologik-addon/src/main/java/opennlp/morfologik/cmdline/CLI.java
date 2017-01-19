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

package opennlp.morfologik.cmdline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import opennlp.morfologik.cmdline.builder.MorfologikDictionaryBuilderTool;
import opennlp.morfologik.cmdline.builder.XMLDictionaryToTableTool;
import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CmdLineTool;
import opennlp.tools.cmdline.StreamFactoryRegistry;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.cmdline.TypedCmdLineTool;
import opennlp.tools.util.Version;

public final class CLI {

  private static final String CMD = "opennlp-morfologik-addon";

  private static Map<String, CmdLineTool> toolLookupMap;

  static {
    toolLookupMap = new LinkedHashMap<>();

    List<CmdLineTool> tools = new LinkedList<>();

    tools.add(new MorfologikDictionaryBuilderTool());
    tools.add(new XMLDictionaryToTableTool());

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

  private static void usage() {
    System.out.print("OpenNLP Morfologik Addon "
        + Version.currentVersion().toString() + ". ");
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

      for (int i = 0; i < Math.abs(tool.getName().length()
          - numberOfSpaces); i++) {
        System.out.print(" ");
      }

      System.out.println(tool.getShortDescription());
    }

    System.out.println("All tools print help when invoked with help parameter");
    System.out.println("Example: opennlp-morfologik-addon POSDictionaryBuilder help");
  }


  @SuppressWarnings("rawtypes")
  public static void main(String[] args) {

    if (args.length == 0) {
      usage();
      System.exit(0);
    }

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
    } catch (TerminateToolException e) {

      if (e.getMessage() != null) {
        System.err.println(e.getMessage());
      }

      if (e.getCause() != null) {
        System.err.println(e.getCause().getMessage());
        e.getCause().printStackTrace(System.err);
      }

      System.exit(e.getCode());
    }
  }
}
