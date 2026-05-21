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
 * Annotation-driven parameters for {@link CorrectTextTool}.
 */
interface CorrectTextParams extends EncodingParameter {

  @ParameterDescription(valueName = "model",
      description = "A binary SymSpell model file. Either -model or -lang must be given.")
  @OptionalParameter
  File getModel();

  @ParameterDescription(valueName = "lang",
      description = "Resolve a packaged spellcheck model for this language from the classpath. "
          + "Either -model or -lang must be given.")
  @OptionalParameter
  String getLang();

  @ParameterDescription(valueName = "in",
      description = "The input text file (read line by line). If absent, standard input is used.")
  @OptionalParameter
  File getInputFile();

  @ParameterDescription(valueName = "out",
      description = "The output text file. If absent, standard output is used.")
  @OptionalParameter
  File getOutputFile();

  @ParameterDescription(valueName = "true|false",
      description = "Correct each line as a whole phrase, repairing wrong word splits/merges.")
  @OptionalParameter(defaultValue = "false")
  Boolean getCompound();

  @ParameterDescription(valueName = "num",
      description = "The maximum edit distance considered per token.")
  @OptionalParameter(defaultValue = "2")
  Integer getMaxEditDistance();

  @ParameterDescription(valueName = "true|false",
      description = "List the suggestions for each token (honoring -verbosity) instead of "
          + "emitting corrected text.")
  @OptionalParameter(defaultValue = "false")
  Boolean getSuggest();

  @ParameterDescription(valueName = "TOP|CLOSEST|ALL",
      description = "How many suggestions to list per token; only used with -suggest true. "
          + "TOP=best only, CLOSEST=all at the smallest distance, ALL=all within -maxEditDistance.")
  @OptionalParameter(defaultValue = "TOP")
  String getVerbosity();
}
