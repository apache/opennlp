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
 * Interface for tools which support processing of samples of some type
 * coming from a stream of a certain format.
 */
public interface TypedCmdLineTool extends CmdLineTool {

  /**
   * Executes the tool with the given parameters.
   *
   * @param format format to work with
   * @param args command line arguments
   */
  void run(String format, String args[]);

  /**
   * Retrieves a description on how to use the tool.
   *
   * @param format data format
   * @return a description on how to use the tool
   */
  String getHelp(String format);
}
