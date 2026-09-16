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

package opennlp.embeddings.cmdline;

import java.io.IOException;
import java.util.List;

import opennlp.embeddings.corpus.BouvierDictionaryParser;
import opennlp.embeddings.corpus.DictionaryEntry;
import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.TerminateToolException;

/**
 * Normalizes the raw Bouvier HTML transcription into the dictionary TSV interchange
 * file. See {@link BouvierDictionaryParser} for the entry rules.
 */
public class NormalizeDictionaryTool extends BasicCmdLineTool {

  interface Params extends NormalizeDictionaryParams {
  }

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Normalizes raw Bouvier law dictionary HTML into a headword/definition TSV";
  }

  /** {@inheritDoc} */
  @Override
  public String getHelp() {
    return getBasicHelp(Params.class);
  }

  /** {@inheritDoc} */
  @Override
  public void run(String[] args) {
    final Params params = validateAndParseParams(args, Params.class);
    final List<DictionaryEntry> entries;
    try {
      entries = BouvierDictionaryParser.parseDirectory(params.getRawDir().toPath());
      DictionaryEntry.writeTsv(entries, params.getOut().toPath());
    } catch (IllegalArgumentException e) {
      throw new TerminateToolException(1, e.getMessage(), e);
    } catch (IOException e) {
      throw new TerminateToolException(-1,
          "IO error while normalizing " + params.getRawDir() + ": " + e.getMessage(), e);
    }
    System.out.println("Wrote " + entries.size() + " entries to " + params.getOut());
  }
}
