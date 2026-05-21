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

import java.io.File;

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.params.EncodingParameter;

/**
 * Annotation-driven parameters for {@link SpellCheckModelBuilderTool}.
 */
interface SpellCheckModelBuilderParams extends EncodingParameter {

  @ParameterDescription(valueName = "lang",
      description = "The language tag of the model (e.g. en), stored as model.language.")
  String getLang();

  @ParameterDescription(valueName = "in",
      description = "The unigram frequency dictionary file (word<TAB>count per line).")
  File getUnigrams();

  @ParameterDescription(valueName = "in",
      description = "An optional bigram frequency dictionary file (w1 w2<TAB>count per line).")
  @OptionalParameter
  File getBigrams();

  @ParameterDescription(valueName = "out",
      description = "The output file the binary SymSpell model is written to.")
  File getModel();

  @ParameterDescription(valueName = "num",
      description = "The largest precomputed dictionary edit distance.")
  @OptionalParameter(defaultValue = "2")
  Integer getMaxEditDistance();

  @ParameterDescription(valueName = "num",
      description = "Number of leading symbols used for delete generation "
          + "(must be > maxEditDistance).")
  @OptionalParameter(defaultValue = "7")
  Integer getPrefixLength();

  @ParameterDescription(valueName = "num",
      description = "Minimum corpus count for a term to be indexed.")
  @OptionalParameter(defaultValue = "1")
  Integer getCountThreshold();

  @ParameterDescription(valueName = "damerau-osa|levenshtein",
      description = "The edit-distance metric used for verification.")
  @OptionalParameter(defaultValue = "damerau-osa")
  String getDistance();

  @ParameterDescription(valueName = "num",
      description = "Corpus size N used for compound-correction scoring; 0 (default) derives "
          + "N from the dictionary's summed counts.")
  @OptionalParameter(defaultValue = "0")
  Long getCorpusWordCount();
}
