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

package opennlp.tools.cmdline.sentdetect;

import java.io.File;

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;
import opennlp.tools.cmdline.params.BasicTrainingParams;
import opennlp.tools.commons.Internal;

/**
 * TrainingParams for Sentence Detector.
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
@Internal
interface TrainingParams extends BasicTrainingParams {

  @ParameterDescription(valueName = "path", description = "abbreviation dictionary in XML format.")
  @OptionalParameter
  File getAbbDict();

  @ParameterDescription(valueName = "string", description = "EOS characters.")
  @OptionalParameter
  String getEosChars();

  @ParameterDescription(valueName = "factoryName",
      description = "A sub-class of SentenceDetectorFactory where to get implementation and resources.")
  @OptionalParameter
  String getFactory();

  @ParameterDescription(valueName = "useTokenEnd",
      description = "A boolean parameter to detect the start index of the next sentence in the test data.")
  @OptionalParameter(defaultValue = "true")
  Boolean getUseTokenEnd();
}
