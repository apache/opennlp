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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

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
 * <p>Usage: {@code opennlp StopwordFilter <lang|file>}. The single argument is
 * either an ISO 639 language code matching one of the bundled lists, or a path
 * to a custom stopword list file (one entry per line, {@code #} comments and
 * blank lines ignored, loaded case-insensitively). The tokens to filter are
 * always read from standard input. A bundled language code takes precedence;
 * to force loading a file whose name happens to be a language code, qualify it
 * with a path (e.g. {@code ./en}).
 */
public final class StopwordFilterTool extends BasicCmdLineTool {

  @Override
  public String getShortDescription() {
    return "filters stop words from tokens read on stdin";
  }

  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " <lang|file>\n"
        + "  <lang> ISO 639 code of a bundled list; supported: "
        + StopwordLists.supportedLanguages() + "\n"
        + "  <file> path to a custom stopword list (one entry per line; "
        + "'#' comments and blank lines ignored)";
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

    final StopwordFilter filter = resolveFilter(args[0]);

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

  /**
   * Resolves the {@code <lang|file>} argument to a {@link StopwordFilter}. A
   * bundled language code is preferred; otherwise the argument is treated as a
   * path to a custom stopword list file loaded via
   * {@link StopwordLists#load(InputStream, java.nio.charset.Charset, boolean)}.
   */
  private static StopwordFilter resolveFilter(final String source) {
    final StopwordFilter bundled = tryBundled(source);
    if (bundled != null) {
      return bundled;
    }

    final Path path;
    try {
      path = Paths.get(source);
    } catch (final InvalidPathException e) {
      throw new TerminateToolException(1, neitherMessage(source));
    }

    try (InputStream in = Files.newInputStream(path)) {
      return StopwordLists.load(in, StandardCharsets.UTF_8, false);
    } catch (final NoSuchFileException e) {
      throw new TerminateToolException(1, neitherMessage(source));
    } catch (final IOException e) {
      throw new TerminateToolException(1,
          "Error reading stopword list file '" + source + "': " + e.getMessage(), e);
    }
  }

  /**
   * @return A bundled {@link StopwordFilter} for {@code code}, or {@code null}
   *     if {@code code} is not a supported bundled ISO 639 language code.
   */
  private static StopwordFilter tryBundled(final String code) {
    try {
      return StopwordLists.forLanguage(code);
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  private static String neitherMessage(final String source) {
    return "'" + source + "' is neither a supported language code "
        + StopwordLists.supportedLanguages() + " nor an existing file.";
  }
}
