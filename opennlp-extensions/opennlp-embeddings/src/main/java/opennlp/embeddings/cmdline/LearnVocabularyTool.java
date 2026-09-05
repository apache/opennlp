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
import java.util.ArrayList;
import java.util.List;

import opennlp.embeddings.corpus.CasePassage;
import opennlp.embeddings.corpus.DictionaryEntry;
import opennlp.embeddings.corpus.TermCount;
import opennlp.embeddings.corpus.VocabularyLearner;
import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.util.InvalidFormatException;

/**
 * Learns a vocabulary from the dictionary and passage interchange files. See
 * {@link VocabularyLearner} for the counting and selection rules.
 */
public class LearnVocabularyTool extends BasicCmdLineTool {

  interface Params extends LearnVocabularyParams {
  }

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Learns a term vocabulary from a dictionary TSV and an opinion-passage JSONL corpus";
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
    final List<TermCount> vocabulary;
    long dictionaryTerms;
    try {
      final List<DictionaryEntry> entries =
          DictionaryEntry.readTsv(params.getDictionary().toPath());
      final List<String> headwords = new ArrayList<>(entries.size());
      for (DictionaryEntry entry : entries) {
        headwords.add(entry.headword());
      }
      final List<CasePassage> passages = CasePassage.readJsonl(params.getPassages().toPath());
      final List<String> texts = new ArrayList<>(passages.size());
      for (CasePassage passage : passages) {
        texts.add(passage.text());
      }
      vocabulary = new VocabularyLearner(params.getMinFrequency(), params.getMaxTerms())
          .learn(texts, headwords);
      TermCount.writeTsv(vocabulary, params.getOut().toPath());
      dictionaryTerms = vocabulary.stream().filter(TermCount::fromDictionary).count();
    } catch (IllegalArgumentException | InvalidFormatException e) {
      throw new TerminateToolException(1, e.getMessage(), e);
    } catch (IOException e) {
      throw new TerminateToolException(-1,
          "IO error while learning the vocabulary: " + e.getMessage(), e);
    }
    System.out.println("Wrote " + vocabulary.size() + " terms (" + dictionaryTerms
        + " dictionary, " + (vocabulary.size() - dictionaryTerms) + " corpus) to "
        + params.getOut());
  }
}
