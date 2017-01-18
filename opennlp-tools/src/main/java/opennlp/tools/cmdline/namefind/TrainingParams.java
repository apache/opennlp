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

package opennlp.tools.cmdline.namefind;

import java.io.File;

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.params.BasicTrainingParams;

/**
 * TrainingParameters for Name Finder.
 *
 * Note: Do not use this class, internal use only!
 */
interface TrainingParams extends BasicTrainingParams {

  @ParameterDescription(valueName = "modelType", description = "The type of the token name finder model")
  @OptionalParameter
  String getType();

  @ParameterDescription(valueName = "resourcesDir", description = "The resources directory")
  @OptionalParameter
  File getResources();

  @ParameterDescription(valueName = "featuregenFile", description = "The feature generator descriptor file")
  @OptionalParameter
  File getFeaturegen();

  @OptionalParameter
  @ParameterDescription(valueName = "types", description = "name types to use for training")
  String getNameTypes();

  @OptionalParameter(defaultValue = "opennlp.tools.namefind.BioCodec")
  @ParameterDescription(valueName = "codec", description = "sequence codec used to code name spans")
  String getSequenceCodec();

  @ParameterDescription(valueName = "factoryName", description = "A sub-class of TokenNameFinderFactory")
  @OptionalParameter
  String getFactory();
}
