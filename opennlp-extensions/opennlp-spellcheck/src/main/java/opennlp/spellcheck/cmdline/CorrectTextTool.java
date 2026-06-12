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

package opennlp.spellcheck.cmdline;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import opennlp.spellcheck.SpellChecker;
import opennlp.spellcheck.SuggestItem;
import opennlp.spellcheck.Verbosity;
import opennlp.spellcheck.dictionary.SymSpellModel;
import opennlp.spellcheck.dictionary.SymSpellModelResolver;
import opennlp.spellcheck.dictionary.SymSpellModels;
import opennlp.spellcheck.normalizer.SpellCheckingCharSequenceNormalizer;
import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;

/**
 * A command line tool that corrects spelling in text using a {@link SymSpellModel}.
 *
 * <p>The model is either loaded from a file ({@code -model}) or resolved from the classpath
 * by language ({@code -lang}) via {@link SymSpellModelResolver}. Input is read line by line
 * from a file ({@code -inputFile}) or, when absent, from standard input; corrected output is
 * written to a file ({@code -outputFile}) or to standard output.</p>
 *
 * <p>By default each line is corrected token by token. With {@code -compound true} the whole
 * line is corrected as a phrase, additionally repairing wrong word splits and merges. With
 * {@code -suggest true} the tool instead lists the candidate suggestions for every token
 * (honoring {@code -verbosity}) rather than emitting corrected text.</p>
 */
public class CorrectTextTool extends BasicCmdLineTool {

  interface Params extends CorrectTextParams {
  }

  @Override
  public String getShortDescription() {
    return "corrects spelling in text using a SymSpell model";
  }

  @Override
  public String getHelp() {
    // Mirror CmdLineTool.getBasicHelp but with the addon's own command name.
    return "Usage: " + CLI.CMD + " " + getName() + " "
        + ArgumentParser.createUsage(Params.class);
  }

  @Override
  public void run(String[] args) {
    final Params params = validateAndParseParams(args, Params.class);

    final Charset encoding = params.getEncoding();
    final int maxEditDistance = params.getMaxEditDistance();
    final SymSpellModel model = loadModel(params);

    final File inputFile = params.getInputFile();
    final File outputFile = params.getOutputFile();
    if (inputFile != null) {
      CmdLineUtil.checkInputFile("input text file", inputFile);
    }
    if (outputFile != null) {
      CmdLineUtil.checkOutputFile("output text file", outputFile);
    }

    try (BufferedReader in = openReader(inputFile, encoding);
         BufferedWriter out = openWriter(outputFile, encoding)) {
      if (params.getSuggest()) {
        listSuggestions(model, params, maxEditDistance, in, out);
      } else {
        correctText(model, params, maxEditDistance, in, out);
      }
      out.flush();
    } catch (IOException e) {
      throw new TerminateToolException(-1, "IO error while correcting text: " + e.getMessage(), e);
    }
  }

  /** Emits the corrected text, line by line (default behavior). */
  private static void correctText(SymSpellModel model, Params params, int maxEditDistance,
                                  BufferedReader in, BufferedWriter out) throws IOException {
    final SpellCheckingCharSequenceNormalizer.Mode mode = params.getCompound()
        ? SpellCheckingCharSequenceNormalizer.Mode.COMPOUND
        : SpellCheckingCharSequenceNormalizer.Mode.PER_TOKEN;
    final SpellCheckingCharSequenceNormalizer normalizer =
        SpellCheckingCharSequenceNormalizer.builder(model)
            .mode(mode)
            .maxEditDistance(maxEditDistance)
            .build();
    String line;
    while ((line = in.readLine()) != null) {
      out.write(normalizer.normalize(line).toString());
      out.newLine();
    }
  }

  /**
   * Lists the candidate suggestions for each whitespace-delimited token (lower-cased before
   * lookup), honoring the requested {@link Verbosity}, as {@code token => [s1, s2, ...]}.
   */
  private static void listSuggestions(SymSpellModel model, Params params, int maxEditDistance,
                                      BufferedReader in, BufferedWriter out) throws IOException {
    final SpellChecker checker = model.getSymSpell();
    final Verbosity verbosity = parseVerbosity(params.getVerbosity());
    // A direct lookup throws if the requested distance exceeds the engine's; clamp to it.
    final int effectiveMax = Math.min(maxEditDistance, checker.maxEditDistance());
    String line;
    while ((line = in.readLine()) != null) {
      if (line.isBlank()) {
        out.newLine();
        continue;
      }
      for (String token : line.trim().split("\\s+")) {
        final List<SuggestItem> suggestions =
            checker.lookup(token.toLowerCase(Locale.ROOT), verbosity, effectiveMax);
        final StringBuilder terms = new StringBuilder();
        for (SuggestItem item : suggestions) {
          if (terms.length() > 0) {
            terms.append(", ");
          }
          terms.append(item.term());
        }
        out.write(token + " => [" + terms + "]");
        out.newLine();
      }
    }
  }

  private static Verbosity parseVerbosity(String name) {
    try {
      return Verbosity.valueOf(name.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new TerminateToolException(1,
          "Unknown verbosity '" + name + "', expected TOP, CLOSEST or ALL.");
    }
  }

  private static SymSpellModel loadModel(Params params) {
    final File modelFile = params.getModel();
    final String lang = params.getLang();

    if (modelFile == null && (lang == null || lang.isBlank())) {
      throw new TerminateToolException(1, "Either -model or -lang must be specified.");
    }

    if (modelFile != null) {
      CmdLineUtil.checkInputFile("spellcheck model file", modelFile);
      try (InputStream in = Files.newInputStream(modelFile.toPath())) {
        return SymSpellModels.deserialize(in);
      } catch (IOException e) {
        throw new TerminateToolException(-1,
            "Error while loading spellcheck model: " + e.getMessage(), e);
      }
    }

    try {
      final Optional<SymSpellModel> resolved = new SymSpellModelResolver().resolveByLanguage(lang);
      return resolved.orElseThrow(() -> new TerminateToolException(1,
          "No spellcheck model for language '" + lang + "' found on the classpath. "
              + "Add an " + SymSpellModels.artifactId(lang) + " jar or use -model."));
    } catch (IOException e) {
      throw new TerminateToolException(-1,
          "Error while resolving spellcheck model for language '" + lang + "': "
              + e.getMessage(), e);
    }
  }

  private static BufferedReader openReader(File inputFile, Charset encoding) throws IOException {
    // When reading from stdin, shield System.in from being closed by the try-with-resources.
    final InputStream in = inputFile != null
        ? Files.newInputStream(inputFile.toPath())
        : new FilterInputStream(System.in) {
            @Override
            public void close() {
              // keep System.in open for the rest of the JVM
            }
          };
    return new BufferedReader(new InputStreamReader(in, encoding));
  }

  private static BufferedWriter openWriter(File outputFile, Charset encoding) throws IOException {
    // When writing to stdout, flush but do not close System.out on try-with-resources exit.
    final OutputStream out = outputFile != null
        ? Files.newOutputStream(outputFile.toPath())
        : new FilterOutputStream(System.out) {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
              this.out.write(b, off, len); // avoid FilterOutputStream's byte-at-a-time path
            }

            @Override
            public void close() throws IOException {
              this.out.flush(); // keep System.out open for the rest of the JVM
            }
          };
    final Writer writer = new OutputStreamWriter(out, encoding);
    return new BufferedWriter(writer);
  }
}
