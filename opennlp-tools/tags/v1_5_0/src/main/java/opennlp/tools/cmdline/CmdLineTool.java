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

/**
 * A tool which can be executed from the command line.
 * <p>
 * <b>Note:</b> Do not use this class, internal use only!
 */
public interface CmdLineTool {
  
  /**
   * Retrieves the name of the training data tool. The name (used as command)
   * must not contain white spaces.
   * 
   * @return the name of the command line tool
   */
  String getName();
  
  /**
   * Retrieves a short description of what the tool does.
   * 
   * @return
   */
  String getShortDescription();
  
  /**
   * Retrieves a description on how to use the tool.
   * 
   * @return
   */
  String getHelp();
  
  /**
   * Executes the tool with the given parameters.
   * 
   * @param args
   */
  void run(String args[]);
}
