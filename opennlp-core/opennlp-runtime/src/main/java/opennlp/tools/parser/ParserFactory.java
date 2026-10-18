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

import opennlp.tools.chunker.Chunker;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.postag.POSTagger;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.util.ParamChecks;

public class ParserFactory {

  private ParserFactory() {
  }

  /**
   * Instantiates a {@link Parser} via a given {@code model} and
   * other configuration parameters.
   *
   * @param model The {@link ParserModel} to use. Must not be {@code null}.
   * @param beamSize The number of different parses kept during parsing. Must be at least
   *                 {@code 1}.
   * @param advancePercentage The minimal amount of probability mass which advanced outcomes
   *                          must represent. Only outcomes which contribute to the top
   *                          {@code advancePercentage} will be explored. Must be greater
   *                          than {@code 0} and at most {@code 1}.
   *
   * @return A valid {@link Parser} instance.
   * @throws IllegalArgumentException Thrown if {@code model} is {@code null} or
   *                                  a parameter is out of range.
   * @throws IllegalStateException Thrown if the {@link ParserType} is not supported.
   *
   * @see Parser
   * @see ParserModel
   */
  public static Parser create(ParserModel model, int beamSize, double advancePercentage) {
    ParamChecks.requireNonNullArg(model, "model");
    return create(model, new POSTaggerME(model.getParserTaggerModel()),
        new ChunkerME(model.getParserChunkerModel()), beamSize, advancePercentage);
  }

  /**
   * Instantiates a {@link Parser} for the {@link ParserType} of a given {@code model} that tags
   * and chunks with a caller-supplied {@link POSTagger} and {@link Chunker}. The build, attach
   * and check models and the head rules are taken from {@code model}; its tagger and chunker
   * models are not used.
   *
   * @param model The {@link ParserModel} to use. Must not be {@code null}.
   * @param tagger The {@link POSTagger} used to tag. Must not be {@code null}.
   * @param chunker The {@link Chunker} used to chunk. Must not be {@code null}.
   * @param beamSize The number of different parses kept during parsing. Must be at least
   *                 {@code 1}.
   * @param advancePercentage The minimal amount of probability mass which advanced outcomes
   *                          must represent. Only outcomes which contribute to the top
   *                          {@code advancePercentage} will be explored. Must be greater
   *                          than {@code 0} and at most {@code 1}.
   *
   * @return A valid {@link Parser} instance.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null} or out of range.
   * @throws IllegalStateException Thrown if the {@link ParserType} is not supported.
   *
   * @see Parser
   * @see ParserModel
   * @since 3.0.0
   */
  public static Parser create(ParserModel model, POSTagger tagger, Chunker chunker,
                              int beamSize, double advancePercentage) {
    ParserType type = ParamChecks.requireNonNullArg(model, "model").getParserType();
    if (ParserType.CHUNKING.equals(type)) {
      return new opennlp.tools.parser.chunking.Parser(model, tagger, chunker,
          beamSize, advancePercentage);
    }
    else if (ParserType.TREEINSERT.equals(type)) {
      return new opennlp.tools.parser.treeinsert.Parser(model, tagger, chunker,
          beamSize, advancePercentage);
    }
    else {
      throw new IllegalStateException("Unexpected ParserType: " + type);
    }
  }

  /**
   * Instantiates a {@link Parser} via a given {@code model} and
   * default configuration parameters (see: {@link AbstractBottomUpParser}).
   *
   * @param model The {@link ParserModel} to use.
   *
   * @return A valid {@link Parser} instance.
   * @throws IllegalArgumentException Thrown if {@code model} is {@code null}.
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
