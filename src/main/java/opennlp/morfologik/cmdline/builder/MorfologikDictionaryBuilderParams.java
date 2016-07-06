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

import morfologik.stemming.EncoderType;
import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.params.EncodingParameter;

/**
 * Params for Dictionary tools.
 */
interface MorfologikDictionaryBuilderParams extends EncodingParameter {

  @ParameterDescription(valueName = "in", description = "Plain file with one entry per line")
  File getInputFile();

  @ParameterDescription(valueName = "out", description = "The generated dictionary file.")
  File getOutputFile();

  @ParameterDescription(valueName = "sep", description = "The FSA dictionary separator. Default is '+'.")
  @OptionalParameter(defaultValue = "+")
  String getFSADictSeparator();
  
  @ParameterDescription(valueName = "sep", description = "The type of lemma-inflected form encoding compression that precedes automaton construction. Allowed values: [suffix, infix, prefix, none]. Details are in Daciuk's paper and in the code. ")
  @OptionalParameter(defaultValue = "prefix")
  EncoderType getEncoderType();

}
