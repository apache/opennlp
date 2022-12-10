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
 * Defines common methods for full-syntactic parsers.
 */
public interface Parser {

  /**
   * Returns the specified number of parses or fewer for the specified tokens.
   * <p>
   *
   * <b>Note:</b> The nodes within
   * the returned parses are shared with other parses and therefore their parent node references
   * will not be consistent with their child node reference.
   * <p>
   *
   * {@link Parse#setParent(Parse)} can be used to make the parents consistent with a
   * particular parse, but subsequent calls to <code>setParents</code> can invalidate the
   * results of earlier calls.<br>
   *
   * @param tokens A {@link Parse} containing the tokens with a single parent node.
   * @param numParses The number of parses desired.
   * @return the specified number of {@link Parse parses} for the specified {@code tokens}.
   */
  Parse[] parse(Parse tokens, int numParses);

  /**
   * Returns a {@link Parse} for the specified {@link Parse} of {@code tokens}.
   *
   * @param tokens The root node of a flat parse containing only tokens.
   * @return A full parse of the specified tokens or the flat chunks of the tokens if a
   *         full parse could not be found.
   */
  Parse parse(Parse tokens);
}
