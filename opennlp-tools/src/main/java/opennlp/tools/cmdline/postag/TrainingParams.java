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

package opennlp.tools.cmdline.postag;

import java.io.File;

import opennlp.tools.cmdline.ArgumentParser;
import opennlp.tools.cmdline.params.BasicTrainingParams;

/**
 * TrainingParameters for Name Finder.
 *
 * Note: Do not use this class, internal use only!
 */
interface TrainingParams extends BasicTrainingParams {

  @ArgumentParser.ParameterDescription(valueName = "maxent|perceptron|perceptron_sequence", description = "The type of the token name finder model. One of maxent|perceptron|perceptron_sequence.")
  @ArgumentParser.OptionalParameter(defaultValue = "maxent")
  String getType();

  @ArgumentParser.ParameterDescription(valueName = "dictionaryPath", description = "The XML tag dictionary file")
  @ArgumentParser.OptionalParameter
  File getDict();

  @ArgumentParser.ParameterDescription(valueName = "cutoff", description = "NGram cutoff. If not specified will not create ngram dictionary.")
  @ArgumentParser.OptionalParameter
  Integer getNgram();

  @ArgumentParser.ParameterDescription(valueName = "tagDictCutoff", description = "TagDictionary cutoff. If specified will create/expand a mutable TagDictionary")
  @ArgumentParser.OptionalParameter
  Integer getTagDictCutoff();

  @ArgumentParser.ParameterDescription(valueName = "factoryName", description = "A sub-class of POSTaggerFactory where to get implementation and resources.")
  @ArgumentParser.OptionalParameter
  String getFactory();
}
