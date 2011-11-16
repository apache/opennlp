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

import opennlp.tools.util.ObjectStream;

public interface ObjectStreamFactory<T> {

  /**
   * Returns usage help message.
   * @return help message
   */
  String getUsage();

  /**
   * Validates arguments and returns null if they are valid or error message if they are not.
   * @param args arguments
   * @return returns null if arguments are valid or error message if they are not
   */
  String validateArguments(String args[]);
  
  /**
   * Creates the <code>ObjectStream</code>.
   * 
   * @param args arguments
   * @return ObjectStream instance
   */
  ObjectStream<T> create(String args[]);
}
