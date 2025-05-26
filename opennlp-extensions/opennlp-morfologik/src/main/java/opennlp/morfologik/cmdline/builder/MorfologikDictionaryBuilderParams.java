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

package opennlp.morfologik.cmdline.builder;

import java.io.File;

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.params.EncodingParameter;

/**
 * Params for Dictionary tools.
 */
interface MorfologikDictionaryBuilderParams extends EncodingParameter {

  @ParameterDescription(valueName = "in",
      description = "The input file (base,inflected,tag). An associated metadata (*.info) file must exist.")
  File getInputFile();

  @ParameterDescription(valueName = "true|false", description = "Accept leading BOM bytes (UTF-8).")
  @OptionalParameter(defaultValue = "false")
  Boolean getAcceptBOM();

  @ParameterDescription(valueName = "true|false", description = "Accept CR bytes in input sequences (\r).")
  @OptionalParameter(defaultValue = "false")
  Boolean getAcceptCR();

  @ParameterDescription(valueName = "FSA5|CFSA2", description = "Automaton serialization format.")
  @OptionalParameter(defaultValue = "FSA5")
  String getFormat();

  @ParameterDescription(valueName = "true|false", description = "Ignore empty lines in the input.")
  @OptionalParameter(defaultValue = "false")
  Boolean getIgnoreEmpty();

  @ParameterDescription(valueName = "true|false", description = "Overwrite the output file if it exists.")
  @OptionalParameter(defaultValue = "false")
  Boolean getOverwrite();

  @ParameterDescription(valueName = "true|false",
      description = "Validate input to make sure it makes sense.")
  @OptionalParameter(defaultValue = "false")
  Boolean getValidate();
}
