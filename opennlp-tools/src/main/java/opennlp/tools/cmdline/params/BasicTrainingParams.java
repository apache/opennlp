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

package opennlp.tools.cmdline.params;

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;

// TODO: remove the old BasicTrainingParameters and rename this class to BasicTrainingParameters

/**
 * Common training parameters.
 * 
 * Note: Do not use this class, internal use only!
 */
public interface BasicTrainingParams extends EncodingParameter{

  @ParameterDescription(valueName = "language", description = "specifies the language which is being processed.")
  String getLang();
  
  @ParameterDescription(valueName = "num", description = "specifies the number of training iterations. It is ignored if a parameters file is passed.")
  @OptionalParameter(defaultValue="100")
  Integer getIterations();
  
  @ParameterDescription(valueName = "num", description = "specifies the min number of times a feature must be seen. It is ignored if a parameters file is passed.")
  @OptionalParameter(defaultValue="5")
  Integer getCutoff();
  
  @ParameterDescription(valueName = "paramsFile", description = "Training parameters file.")
  @OptionalParameter()
  String getParams();
  
}
