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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Locale;

import opennlp.spellcheck.dictionary.SymSpellModel;
import opennlp.spellcheck.dictionary.SymSpellModels;
import opennlp.spellcheck.distance.DamerauOSADistance;
import opennlp.spellcheck.distance.EditDistance;
import opennlp.spellcheck.distance.LevenshteinDistance;
import opennlp.spellcheck.symspell.SymSpellConfig;
import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CmdLineUtil;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.util.InputStreamFactory;

/**
 * A command line tool that builds a binary {@link SymSpellModel} from plain-text
 * frequency dictionaries.
 *
 * <p>It reads a unigram frequency list (and, optionally, a bigram list), builds a SymSpell
 * engine with the requested edit-distance configuration and serializes the resulting model
 * to an output file using {@link SymSpellModels#serialize(SymSpellModel, OutputStream)}.</p>
 */
public class SpellCheckModelBuilderTool extends BasicCmdLineTool {

  interface Params extends SpellCheckModelBuilderParams {
  }

  @Override
  public String getShortDescription() {
    return "builds a binary SymSpell spellcheck model from frequency dictionaries";
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

    final File unigramFile = params.getUnigrams();
    final File bigramFile = params.getBigrams();
    final File modelFile = params.getModel();
    final Charset encoding = params.getEncoding();

    CmdLineUtil.checkInputFile("unigram dictionary input file", unigramFile);
    if (bigramFile != null) {
      CmdLineUtil.checkInputFile("bigram dictionary input file", bigramFile);
    }
    CmdLineUtil.checkOutputFile("spellcheck model output file", modelFile);

    final SymSpellConfig config = SymSpellConfig.builder()
        .maxDictionaryEditDistance(params.getMaxEditDistance())
        .prefixLength(params.getPrefixLength())
        .countThreshold(params.getCountThreshold())
        .editDistance(resolveDistance(params.getDistance()))
        .corpusWordCount(params.getCorpusWordCount())
        .build();

    final InputStreamFactory unigramSource = CmdLineUtil.createInputStreamFactory(unigramFile);
    final InputStreamFactory bigramSource =
        bigramFile != null ? CmdLineUtil.createInputStreamFactory(bigramFile) : null;

    try {
      final SymSpellModel model =
          SymSpellModels.buildModel(params.getLang(), config, encoding, unigramSource, bigramSource);

      try (OutputStream out = new BufferedOutputStream(
          Files.newOutputStream(modelFile.toPath()))) {
        SymSpellModels.serialize(model, out);
      }
    } catch (IOException e) {
      throw new TerminateToolException(-1,
          "Error while building SymSpell spellcheck model: " + e.getMessage(), e);
    }
  }

  private static EditDistance resolveDistance(String name) {
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "damerau-osa", "damerau", "osa" -> DamerauOSADistance.INSTANCE;
      case "levenshtein", "lev" -> LevenshteinDistance.INSTANCE;
      default -> throw new TerminateToolException(1,
          "Unknown edit distance '" + name + "', expected 'damerau-osa' or 'levenshtein'.");
    };
  }
}
