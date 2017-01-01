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

package opennlp.tools.cmdline.tokenizer;

import java.io.File;

import opennlp.tools.cmdline.params.BasicTrainingParams;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;

/**
 * TrainingParameters for Tokenizer.
 *
 * Note: Do not use this class, internal use only!
 */
interface TrainingParams extends BasicTrainingParams {
  @ParameterDescription(valueName = "isAlphaNumOpt", description = "Optimization flag to skip alpha numeric tokens for further tokenization")
  @OptionalParameter(defaultValue = "false")
  Boolean getAlphaNumOpt();

  @ParameterDescription(valueName = "path", description = "abbreviation dictionary in XML format.")
  @OptionalParameter
  File getAbbDict();

  @ParameterDescription(valueName = "factoryName", description = "A sub-class of TokenizerFactory where to get implementation and resources.")
  @OptionalParameter
  String getFactory();
}
