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

package opennlp.tools.cmdline;

public class BasicTrainingParameters {

  private String language;
  private String encoding;
  private int numberOfIterations = 100;
  private int cutoff = 5;
  
  public BasicTrainingParameters(String args[]) {
    encoding = CmdLineUtil.getEncodingParameter(args);
    language = CmdLineUtil.getParameter("-lang", args);
    
    Integer numberOfIterations = CmdLineUtil.getIntParameter("-iterations", args); 
    if (numberOfIterations != null)
      this.numberOfIterations = numberOfIterations;
  }
  
  /**
   * Retrieves the mandatory language parameter.
   * 
   * @return
   */
  public String getLanguage() {
    return language;
  }
  
  public String getEncoding() {
    return encoding;
  }
  
  /**
   * Retrieves the optional iterations parameter.
   * 
   * @return specified number or 100 (default)
   */
  public int getNumberOfIterations() {
    return numberOfIterations;
  }
  
  public int getCutoff() {
    return cutoff;
  }
  
  public boolean isValid() {
    return language != null && encoding != null;
  }
  
  public static String getParameterUsage() {
    return "-lang language -encoding charset [-iterations num] [-cutoff num]";
  }
  
  public static String getDescription() {
    return 
        "-encoding charset specifies the encoding which should be used" +
        " for reading and writing text.\n" + 
        "-lang language    specifies the language which " +
        "is being processed.";
  }
}
