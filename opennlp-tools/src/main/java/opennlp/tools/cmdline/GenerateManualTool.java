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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import opennlp.tools.cmdline.ArgumentParser.Argument;

public class GenerateManualTool {

  private static final int MAX_LINE_LENGTH = 110; // optimized for printing

  public static void main(String[] args) throws FileNotFoundException {

    if (args.length != 1) {
      System.out.print(getUsage());
      System.exit(0);
    }

    StringBuilder sb = new StringBuilder();

    appendHeader(sb);

    // organize by package name
    LinkedHashMap<String, Map<String, CmdLineTool>> packageNameToolMap = new LinkedHashMap<>();
    for (String toolName : CLI.getToolLookupMap().keySet()) {
      CmdLineTool tool = CLI.getToolLookupMap().get(toolName);
      String packageName = tool.getClass().getPackage().getName();
      packageName = packageName.substring(packageName.lastIndexOf(".") + 1);

      if (!packageNameToolMap.containsKey(packageName)) {
        packageNameToolMap.put(packageName,
            new LinkedHashMap<String, CmdLineTool>());
      }
      packageNameToolMap.get(packageName).put(toolName, tool);
    }

    // add tools grouped by package
    for (String grouName : packageNameToolMap.keySet()) {
      appendToolGroup(grouName, packageNameToolMap.get(grouName), sb);
    }

    // footer
    appendFooter(sb);

    // output to file
    try (PrintWriter out = new PrintWriter(args[0])) {
      out.println(sb);
    }
  }

  /**
   * @return this tool usage
   */
  private static String getUsage() {
    return "Requires one argument: \n" +
            "  Path to the output XML file \n";
  }

  /**
   * Appends a group of tools, based on the tool package name
   *
   * @param groupName
   * @param toolsMap
   * @param sb
   */
  private static void appendToolGroup(String groupName,
      Map<String, CmdLineTool> toolsMap, StringBuilder sb) {
    sb.append("<section id='tools.cli.").append(groupName).append("'>\n\n");
    sb.append("<title>").append(firstCaps(groupName)).append("</title>\n\n");

    for (String toolName : toolsMap.keySet()) {
      appendTool(groupName, toolName, toolsMap.get(toolName), sb);
    }

    sb.append("</section>\n\n");

  }

  /**
   * Appends a tool
   *
   * @param groupName
   * @param toolName
   * @param tool
   * @param sb
   */
  private static void appendTool(String groupName, String toolName,
      CmdLineTool tool, StringBuilder sb) {
    sb.append("<section id='tools.cli.").append(groupName).append(".")
        .append(toolName).append("'>\n\n");
    sb.append("<title>").append(toolName).append("</title>\n\n");
    sb.append("<para>").append(firstCaps(tool.getShortDescription()))
        .append("</para>\n\n");

    appendCode(tool.getHelp(), sb);
    if (TypedCmdLineTool.class.isAssignableFrom(tool.getClass())) {
      appendHelpForTool((TypedCmdLineTool<?>) tool, sb);
    }

    sb.append("</section>\n\n");
  }

  @SuppressWarnings("unchecked")
  private static void appendHelpForTool(TypedCmdLineTool<?> tool,
      StringBuilder sb) {
    Class<?> type = tool.type;

    Set<String> formats = StreamFactoryRegistry.getFactories(type).keySet();
    sb.append("<para>The supported formats and arguments are:</para>\n\n");
    Map<String, List<Argument>> formatArguments = new LinkedHashMap<>();
    for (String formatName : formats) {
      if (!StreamFactoryRegistry.DEFAULT_FORMAT.equals(formatName)) {
        ObjectStreamFactory<?> format = tool.getStreamFactory(formatName);
        formatArguments.put(formatName,
            ArgumentParser.createArguments(format.getParameters()));

      }
    }
    appendArgumentTable(formatArguments, sb);
  }

  private static void appendArgumentTable(
      Map<String, List<Argument>> formatArguments, StringBuilder sb) {
    sb.append(
        "<informaltable frame='all'><tgroup cols='4' align='left' colsep='1' rowsep='1'>\n");

    sb.append(
        "<thead><row><entry>Format</entry><entry>Argument</entry><entry>Value</entry>" +
            "<entry>Optional</entry><entry>Description</entry></row></thead>\n");
    sb.append("<tbody>\n");

    for (String format : formatArguments.keySet()) {
      List<Argument> arguments = formatArguments.get(format);
      int i = 0;
      for (Argument argument : arguments) {
        sb.append("<row>\n");
        if (i == 0) {
          sb.append("<entry morerows='").append(arguments.size() - 1)
              .append("' valign='middle'>").append(format).append("</entry>\n");
        }
        sb.append("<entry>").append(argument.getArgument())
            .append("</entry>\n");
        sb.append("<entry>").append(argument.getValue()).append("</entry>\n");
        sb.append("<entry>").append(yes(argument.getOptional()))
            .append("</entry>\n");
        sb.append("<entry>").append(firstCaps(argument.getDescription()))
            .append("</entry>\n");
        sb.append("</row>\n");
        i++;
      }

    }

    sb.append("</tbody>\n");
    sb.append("</tgroup></informaltable>\n\n");

  }

  private static void appendHeader(StringBuilder sb) {
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<!DOCTYPE chapter PUBLIC \"-//OASIS//DTD DocBook XML V4.4//EN\"\n"
        + "\"http://www.oasis-open.org/docbook/xml/4.4/docbookx.dtd\"[\n"
        + "]>\n" + "<!--\n"
        + "Licensed to the Apache Software Foundation (ASF) under one\n"
        + "or more contributor license agreements.  See the NOTICE file\n"
        + "distributed with this work for additional information\n"
        + "regarding copyright ownership.  The ASF licenses this file\n"
        + "to you under the Apache License, Version 2.0 (the\n"
        + "\"License\"); you may not use this file except in compliance\n"
        + "with the License.  You may obtain a copy of the License at\n" + "\n"
        + "   http://www.apache.org/licenses/LICENSE-2.0\n" + "\n"
        + "Unless required by applicable law or agreed to in writing,\n"
        + "software distributed under the License is distributed on an\n"
        + "\"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY\n"
        + "KIND, either express or implied.  See the License for the\n"
        + "specific language governing permissions and limitations\n"
        + "under the License.\n" + "-->\n" + "\n\n"
        + "<!-- ## Warning ## this content is autogenerated! Please fix issues in to " +
        "opennlp-tools/src/main/java/opennlp/tools/cmdline/GenerateManualTool.java \n"
        + "   and execute the following command in opennlp-tool folder to update this file: \n\n"
        + "      mvn -e -q exec:java \"-Dexec.mainClass=opennlp.tools.cmdline.GenerateManualTool\" "
        + "\"-Dexec.args=../opennlp-docs/src/docbkx/cli.xml\"\n"
        + "-->\n\n" + "<chapter id='tools.cli'>\n\n"
        + "<title>The Command Line Interface</title>\n\n" + "<para>"
        + "This section details the available tools and parameters of the Command Line Interface. "
        + "For a introduction in its usage please refer to <xref linkend='intro.cli'/>.  "
        + "</para>\n\n");
  }

  private static void appendFooter(StringBuilder sb) {
    sb.append("\n\n</chapter>");
  }

  private static String firstCaps(String str) {
    if (str.length() > 1) {
      return str.substring(0, 1).toUpperCase() + str.substring(1);
    } else {
      return str;
    }
  }

  private static String yes(boolean optional) {
    if (optional) {
      return "Yes";
    }
    return "No";
  }

  private static void appendCode(String help, StringBuilder sb) {
    sb.append("<screen>\n" + "<![CDATA[\n").append(splitLongLines(help))
        .append("\n").append("]]>\n").append("</screen> \n");
  }

  /**
   * Prevents long lines. Lines are optimized for printing.
   *
   * @param stringBlock
   * @return
   */
  private static String splitLongLines(String stringBlock) {
    StringBuilder sb = new StringBuilder();
    String line;
    try {
      BufferedReader reader = new BufferedReader(new StringReader(stringBlock));
      while ((line = reader.readLine()) != null) {
        if (line.length() <= MAX_LINE_LENGTH) {
          sb.append(line).append("\n");
        } else {
          StringTokenizer tok = new StringTokenizer(line, " ");
          int lineLen = 0;
          while (tok.hasMoreTokens()) {
            String word = tok.nextToken() + " ";

            if (lineLen + word.length() > MAX_LINE_LENGTH) {
              sb.append("\n        ");
              lineLen = 8;
            }
            sb.append(word);
            lineLen += word.length();
          }
        }
      }
    } catch (Exception e) {
      // nothing to do
    }

    return sb.toString();
  }
}
