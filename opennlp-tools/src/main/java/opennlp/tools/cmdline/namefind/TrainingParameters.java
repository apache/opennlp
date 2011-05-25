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

import opennlp.tools.cmdline.BasicTrainingParameters;
import opennlp.tools.cmdline.CmdLineUtil;

/**
 * This class is responsible to parse and provide the training parameters.
 */
class TrainingParameters extends BasicTrainingParameters {

  private static final String TYPE_PARAM = "-type";
  private static final String FEATURE_GEN_PARAM = "-featuregen";
  
  private String type;
  
  private String featureGeneratorDescription;
  private String resourceDirectory;
  
  TrainingParameters(String args[]) {
    super(args);
   
    type = CmdLineUtil.getParameter(TYPE_PARAM, args);
    
    if (type == null)
      type = "default";
    
    featureGeneratorDescription = CmdLineUtil.getParameter(FEATURE_GEN_PARAM, args);
    
    resourceDirectory = CmdLineUtil.getParameter("-resources", args);
  }
  
  String getType() {
    return type;
  }
  
  String getFeatureGenDescriptorFile() {
    return featureGeneratorDescription;
  }
  
  // TODO: Add parameter to description
  String getResourceDirectory() {
    return resourceDirectory;
  }
  
  public static String getParameterUsage() {
    return BasicTrainingParameters.getParameterUsage() + " [" + TYPE_PARAM +" type]" + " [" + FEATURE_GEN_PARAM +" type]";
  }
  
  public static String getDescription() {
    return BasicTrainingParameters.getDescription() + "\n" +
        TYPE_PARAM + " The type of the token name finder model";
  }
}
