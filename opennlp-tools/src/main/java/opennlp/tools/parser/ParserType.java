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

package opennlp.tools.parser;

/**
 * Enumeration of supported {@link Parser} types.
 */
public enum ParserType {
  CHUNKING,
  TREEINSERT;

  /**
   * @param type The string representation of the requested {@link ParserType}.
   * @return The {@link ParserType} matching {@code type}, {@code null} otherwise.
   */
  public static ParserType parse(String type) {
    if (ParserType.CHUNKING.name().equals(type)) {
      return ParserType.CHUNKING;
    }
    else if (ParserType.TREEINSERT.name().equals(type)) {
      return ParserType.TREEINSERT;
    }
    else {
      return null;
    }
  }
}
