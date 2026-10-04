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
import opennlp.tools.util.ArgumentChecks;

/**
 * Builds a {@link Parser} for a {@link ParserModel} with a caller-supplied
 * {@link POSTagger} or {@link Chunker}. Components that are not set are built
 * from the model, as {@link ParserFactory#create(ParserModel, int, double)} does.
 * <p>
 * Obtain an instance via {@link ParserFactory#builder(ParserModel)}.
 *
 * @see ParserFactory
 * @see Parser
 * @since 3.0.0
 */
public final class ParserBuilder {

  private final ParserModel model;
  private POSTagger tagger;
  private Chunker chunker;
  private int beamSize = AbstractBottomUpParser.defaultBeamSize;
  private double advancePercentage = AbstractBottomUpParser.defaultAdvancePercentage;

  /**
   * Initializes a builder for a given {@code model}.
   *
   * @param model The {@link ParserModel} to use. Must not be {@code null}.
   * @throws IllegalArgumentException Thrown if {@code model} is {@code null}.
   */
  ParserBuilder(ParserModel model) {
    this.model = ArgumentChecks.requireNonNullArg(model, "model");
  }

  /**
   * Sets the {@link POSTagger} the parser tags with. If not set, a {@link POSTaggerME}
   * is built from the model's tagger model.
   *
   * @param tagger The {@link POSTagger} to use. Must not be {@code null}.
   * @return This builder.
   * @throws IllegalArgumentException Thrown if {@code tagger} is {@code null}.
   */
  public ParserBuilder tagger(POSTagger tagger) {
    this.tagger = ArgumentChecks.requireNonNullArg(tagger, "tagger");
    return this;
  }

  /**
   * Sets the {@link Chunker} the parser chunks with. If not set, a {@link ChunkerME}
   * is built from the model's chunker model.
   *
   * @param chunker The {@link Chunker} to use. Must not be {@code null}.
   * @return This builder.
   * @throws IllegalArgumentException Thrown if {@code chunker} is {@code null}.
   */
  public ParserBuilder chunker(Chunker chunker) {
    this.chunker = ArgumentChecks.requireNonNullArg(chunker, "chunker");
    return this;
  }

  /**
   * Sets the number of different parses kept during parsing.
   * Defaults to {@link AbstractBottomUpParser#defaultBeamSize}.
   *
   * @param beamSize The beam size. Must be at least {@code 1}.
   * @return This builder.
   * @throws IllegalArgumentException Thrown if {@code beamSize} is less than {@code 1}.
   */
  public ParserBuilder beamSize(int beamSize) {
    this.beamSize = AbstractBottomUpParser.checkBeamSize(beamSize);
    return this;
  }

  /**
   * Sets the minimal amount of probability mass which advanced outcomes must represent.
   * Only outcomes which contribute to the top {@code advancePercentage} will be explored.
   * Defaults to {@link AbstractBottomUpParser#defaultAdvancePercentage}.
   *
   * @param advancePercentage The advance percentage. Must be greater than {@code 0}
   *                          and at most {@code 1}.
   * @return This builder.
   * @throws IllegalArgumentException Thrown if {@code advancePercentage} is not in {@code (0, 1]}.
   */
  public ParserBuilder advancePercentage(double advancePercentage) {
    this.advancePercentage = AbstractBottomUpParser.checkAdvancePercentage(advancePercentage);
    return this;
  }

  /**
   * Builds the {@link Parser} for the model's {@link ParserType}.
   *
   * @return A valid {@link Parser} instance.
   * @throws IllegalStateException Thrown if the {@link ParserType} is not supported.
   */
  public Parser build() {
    POSTagger parserTagger = tagger != null ? tagger
        : new POSTaggerME(model.getParserTaggerModel());
    Chunker parserChunker = chunker != null ? chunker
        : new ChunkerME(model.getParserChunkerModel());
    ParserType type = model.getParserType();
    if (ParserType.CHUNKING.equals(type)) {
      return new opennlp.tools.parser.chunking.Parser(model, parserTagger, parserChunker,
          beamSize, advancePercentage);
    }
    else if (ParserType.TREEINSERT.equals(type)) {
      return new opennlp.tools.parser.treeinsert.Parser(model, parserTagger, parserChunker,
          beamSize, advancePercentage);
    }
    else {
      throw new IllegalStateException("Unexpected ParserType: " + type);
    }
  }
}
