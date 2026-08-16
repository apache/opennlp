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

import java.io.File;

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;

/**
 * The command-line arguments of {@link LearnVocabularyTool}.
 */
interface LearnVocabularyParams {

  /**
   * {@return the dictionary TSV file whose headwords are always kept}
   */
  @ParameterDescription(valueName = "file",
      description = "the dictionary.tsv file whose headwords are always kept")
  File getDictionary();

  /**
   * {@return the passages JSON Lines file the corpus counts come from}
   */
  @ParameterDescription(valueName = "file",
      description = "the passages.jsonl file the corpus counts come from")
  File getPassages();

  /**
   * {@return the vocabulary TSV file to write}
   */
  @ParameterDescription(valueName = "file",
      description = "the vocabulary.tsv file to write, one term<TAB>count<TAB>source per line")
  File getOut();

  /**
   * {@return the smallest corpus frequency that keeps a non-dictionary word}
   */
  @ParameterDescription(valueName = "n",
      description = "the smallest corpus frequency that keeps a non-dictionary word")
  @OptionalParameter(defaultValue = "2")
  Integer getMinFrequency();

  /**
   * {@return the largest vocabulary size, dictionary terms exempt}
   */
  @ParameterDescription(valueName = "n",
      description = "the largest vocabulary size; dictionary terms are never truncated")
  @OptionalParameter(defaultValue = "50000")
  Integer getMaxTerms();
}
