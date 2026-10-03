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

package opennlp.tools.formats.conllu;

import opennlp.tools.cmdline.TerminateToolException;

public enum ConlluTagset {
  U,
  X;

  /**
   * Resolves the value of a command line {@code tagset} parameter.
   *
   * @param parameter {@code u} for the universal tags or {@code x} for the
   *                  language-specific tags.
   * @return The matching {@link ConlluTagset}. Never {@code null}.
   * @throws IllegalArgumentException Thrown if {@code parameter} is neither {@code u}
   *         nor {@code x}.
   */
  public static ConlluTagset fromParameter(String parameter) {
    if ("u".equals(parameter)) {
      return U;
    }
    if ("x".equals(parameter)) {
      return X;
    }
    throw new IllegalArgumentException("Unknown tagset parameter: " + parameter);
  }

  /**
   * Reads the {@code -tagset} parameter of a command line format factory.
   *
   * @param parameter The parameter value, {@code u} or {@code x}.
   * @return The tagset.
   * @throws TerminateToolException Thrown if the parameter is unknown.
   */
  static ConlluTagset fromFactoryParameter(String parameter) {
    try {
      return fromParameter(parameter);
    } catch (IllegalArgumentException e) {
      throw new TerminateToolException(-1, e.getMessage());
    }
  }
}
