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

import opennlp.tools.cmdline.CmdLineUtil;

/**
 * This class is responsible to parse and provide the training parameters.
 */
class TrainingParameters {

  private String language;
  private int numberOfIterations = 100;
  private boolean isAlphaNumOpt = false;
  private String encoding;
  
  private TrainingParameters() {
  }
  
  /**
   * Retrieves the mandatory language parameter.
   * 
   * @return
   */
  String getLanguage() {
    return language;
  }
  
  /**
   * Retrieves the optional iterations parameter.
   * 
   * @return specified number or 100 (default)
   */
  int getNumberOfIterations() {
    return numberOfIterations;
  }
  
  /**
   * Retrieves the optional alphaNumOpt parameter.
   * 
   * @return if parameter is set true, otherwise false (default)
   */
  boolean isAlphaNumericOptimizationEnabled() {
    return isAlphaNumOpt;
  }
  
  String getEncoding() {
    return encoding;
  }
  
  static String getParameterUsage() {
    return "-lang language -encoding charset [-iterations num] [-alphaNumOpt]";
  }
  
  static String getDescription() {
    return 
        "-encoding charset specifies the encoding which should be used" +
        " for reading and writing text.\n" + 
        "-lang language    specifies the language which " +
        "is being processed.";
  }
  
  private boolean isValid() {
    return language != null && encoding != null;
  }
  
  /**
   * Parses the training parameters.
   * <p>
   * Example: -lang en -encoding UTF-8 [-iterations 21] [-alphaNumOpt true]
   * 
   * @param args
   * 
   * @return the training parameters or null in case the given arguments
   * are invalid
   */
  static TrainingParameters parse(String args[]) {
    TrainingParameters params = new TrainingParameters();
    
    params.encoding = CmdLineUtil.getEncodingParameter(args);
    params.language = CmdLineUtil.getParameter("-lang", args);
    Integer numberOfIterations = CmdLineUtil.getIntParameter("-iterations", args); 
    
    if (numberOfIterations != null)
      params.numberOfIterations = numberOfIterations;
    
    params.isAlphaNumOpt = CmdLineUtil.containsParam("-alphaNumOpt", args);
    
    if (params.isValid()) {
      return params;
    }
    else {
      return null;
    }
  }
}
