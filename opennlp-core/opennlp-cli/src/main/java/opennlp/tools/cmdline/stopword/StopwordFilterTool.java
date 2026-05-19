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

package opennlp.tools.cmdline.stopword;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.stopword.StopwordFilter;
import opennlp.tools.stopword.StopwordLists;

/**
 * A command line tool that filters stop words from whitespace-separated
 * tokens read on standard input and prints the kept tokens to standard
 * output, one input line per output line.
 *
 * <p>Usage: {@code opennlp StopwordFilter <lang>}, where {@code <lang>}
 * is an ISO 639 language code matching one of the bundled lists.
 */
public final class StopwordFilterTool extends BasicCmdLineTool {

  @Override
  public String getShortDescription() {
    return "filters stop words from tokens read on stdin";
  }

  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " <lang>\n"
        + "  <lang> ISO 639 code; supported: " + StopwordLists.supportedLanguages();
  }

  @Override
  public boolean hasParams() {
    return true;
  }

  @Override
  public void run(final String[] args) {
    if (args.length != 1) {
      System.out.println(getHelp());
      return;
    }

    final StopwordFilter filter = StopwordLists.forLanguage(args[0]);

    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(System.in, StandardCharsets.UTF_8));
         PrintWriter writer = new PrintWriter(
             new java.io.OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {

      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty()) {
          writer.println();
          continue;
        }
        final String[] tokens = line.split("\\s+");
        final String[] kept = filter.filter(tokens);
        writer.println(String.join(" ", kept));
      }

      writer.flush();
    } catch (final IOException e) {
      throw new TerminateToolException(1, "Error reading from stdin: " + e.getMessage(), e);
    }
  }
}
