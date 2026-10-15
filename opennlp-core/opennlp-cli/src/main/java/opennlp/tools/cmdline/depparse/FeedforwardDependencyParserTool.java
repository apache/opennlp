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

package opennlp.tools.cmdline.depparse;

import java.io.File;

import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.CLI;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.depparse.FeedforwardDependencyModel;
import opennlp.tools.depparse.FeedforwardDependencyParser;
import opennlp.tools.ml.BeamSearch;

/**
 * Parses POS-tagged sentences from standard input with a {@link FeedforwardDependencyParser}
 * and prints basic CoNLL-U trees.
 */
public class FeedforwardDependencyParserTool extends BasicCmdLineTool {

  /** The option that sets the beam size. */
  static final String BEAM_SIZE_OPTION = "-beamSize";

  /** The beam size at which decoding is greedy. */
  private static final int GREEDY_BEAM_SIZE = 1;

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Parses dependency trees from POS-tagged sentences with the feedforward parser";
  }

  /** {@inheritDoc} */
  @Override
  public String getHelp() {
    return "Usage: " + CLI.CMD + " " + getName() + " [" + BEAM_SIZE_OPTION + " n] model"
        + " < tagged-sentences\n"
        + BEAM_SIZE_OPTION + " n: keep n transition sequences per step. Defaults to the beam"
        + " size the model was refined with, or 1 (greedy) for a model that was not refined.";
  }

  /** {@inheritDoc} */
  @Override
  public void run(String[] args) {
    final Integer beamSize;
    final String modelFile;
    if (args.length == 1) {
      beamSize = null;
      modelFile = args[0];
    } else if (args.length == 3 && BEAM_SIZE_OPTION.equals(args[0])) {
      beamSize = parseBeamSize(args[1]);
      modelFile = args[2];
    } else {
      throw new TerminateToolException(1, getHelp());
    }
    DependencyParserMETool.parseTaggedSentences(
        createParser(new FeedforwardDependencyModelLoader().load(new File(modelFile)), beamSize));
  }

  /**
   * @param value The {@value #BEAM_SIZE_OPTION} argument.
   * @return The beam size. Not checked for range.
   * @throws TerminateToolException Thrown if {@code value} is not a decimal integer.
   */
  private static int parseBeamSize(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new TerminateToolException(1, BEAM_SIZE_OPTION + " must be an integer: " + value, e);
    }
  }

  /**
   * Creates a parser with the requested beam size, or the beam size of the model when
   * none is requested.
   *
   * @param model The model to parse with. Must not be {@code null}.
   * @param requested The beam size given on the command line, or {@code null}.
   * @return The parser. Never {@code null}.
   * @throws TerminateToolException Thrown if the beam size is not positive or the model
   *         records an invalid beam size.
   */
  static FeedforwardDependencyParser createParser(FeedforwardDependencyModel model,
      Integer requested) {
    try {
      return new FeedforwardDependencyParser(model, beamSize(model, requested));
    } catch (IllegalArgumentException e) {
      throw new TerminateToolException(-1, e.getMessage(), e);
    }
  }

  /**
   * Resolves the beam size to parse with. A model refined with beam search records its
   * beam size in the manifest under {@value BeamSearch#BEAM_SIZE_PARAMETER}; a locally
   * trained model records none and is parsed greedily.
   *
   * @param model The model to parse with. Must not be {@code null}.
   * @param requested The beam size given on the command line, or {@code null}.
   * @return {@code requested} if not {@code null}, else the beam size of the model, else
   *         {@code 1}. Not checked for range.
   * @throws TerminateToolException Thrown if the model records a beam size that is not
   *         a decimal integer.
   */
  static int beamSize(FeedforwardDependencyModel model, Integer requested) {
    if (requested != null) {
      return requested;
    }
    final String recorded = model.getManifestProperty(BeamSearch.BEAM_SIZE_PARAMETER);
    if (recorded == null) {
      return GREEDY_BEAM_SIZE;
    }
    try {
      return Integer.parseInt(recorded);
    } catch (NumberFormatException e) {
      throw new TerminateToolException(-1, "The model records an invalid "
          + BeamSearch.BEAM_SIZE_PARAMETER + ": " + recorded, e);
    }
  }
}
