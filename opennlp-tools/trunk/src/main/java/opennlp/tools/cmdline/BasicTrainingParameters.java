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

import java.nio.charset.Charset;

public class BasicTrainingParameters {

  private final String language;
  private final Charset encoding;
  private final int iterations;
  private final int cutoff;
  
  public BasicTrainingParameters(String args[]) {
    encoding = CmdLineUtil.getEncodingParameter(args);
    language = CmdLineUtil.getParameter("-lang", args);
    
    Integer iterationsParameter = CmdLineUtil.getIntParameter("-iterations", args); 
    if (iterationsParameter != null)
      this.iterations = iterationsParameter;
    else
      this.iterations = 100;
    
    Integer cutoffParameter = CmdLineUtil.getIntParameter("-cutoff", args);
    if (cutoffParameter != null)
      this.cutoff = cutoffParameter;
    else
      this.cutoff = 5;
  }
  
  /**
   * Retrieves the mandatory language parameter.
   * 
   * @return
   */
  public String getLanguage() {
    return language;
  }
  
  public Charset getEncoding() {
    return encoding;
  }
  
  /**
   * Retrieves the optional iterations parameter.
   * 
   * @return specified number or 100 (default)
   */
  public int getNumberOfIterations() {
    return iterations;
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
        "-lang language     specifies the language which " +
        "is being processed.\n" +
        "-encoding charset  specifies the encoding which should be used" +
        " for reading and writing text.\n" +
        "-iterations num    specified the number of training iterations\n" +
        "-cutoff num        specifies the min number of times a feature must be seen"; 
  }
}
