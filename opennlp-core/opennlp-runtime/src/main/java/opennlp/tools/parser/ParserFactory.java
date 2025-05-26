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

public class ParserFactory {

  private ParserFactory() {
  }

  /**
   * Instantiates a {@link Parser} via a given {@code model} and
   * other configuration parameters.
   *
   * @param model The {@link ParserModel} to use.
   * @param beamSize The number of different parses kept during parsing.
   * @param advancePercentage The minimal amount of probability mass which advanced outcomes
   *                          must represent. Only outcomes which contribute to the top
   *                          {@code advancePercentage} will be explored.
   *
   * @return A valid {@link Parser} instance.
   * @throws IllegalStateException Thrown if the {@link ParserType} is not supported.
   *
   * @see Parser
   * @see ParserModel
   */
  public static Parser create(ParserModel model, int beamSize, double advancePercentage) {

    if (ParserType.CHUNKING.equals(model.getParserType())) {
      return new opennlp.tools.parser.chunking.Parser(model, beamSize, advancePercentage);
    }
    else if (ParserType.TREEINSERT.equals(model.getParserType())) {
      return new opennlp.tools.parser.treeinsert.Parser(model, beamSize, advancePercentage);
    }
    else {
      throw new IllegalStateException("Unexpected ParserType: " +
          model.getParserType().name());
    }
  }

  /**
   * Instantiates a {@link Parser} via a given {@code model} and
   * default configuration parameters (see: {@link AbstractBottomUpParser}).
   *
   * @param model The {@link ParserModel} to use.
   *
   * @return A valid {@link Parser} instance.
   * @throws IllegalStateException Thrown if the {@link ParserType} is not supported.
   *
   * @see Parser
   * @see AbstractBottomUpParser
   */
  public static Parser create(ParserModel model) {
    return create(model, AbstractBottomUpParser.defaultBeamSize,
        AbstractBottomUpParser.defaultAdvancePercentage);
  }
}
