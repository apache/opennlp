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
package opennlp.embeddings.cmdline;

import java.io.File;
import java.io.IOException;

import opennlp.embeddings.ModelAssembler;
import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.util.InvalidFormatException;

/**
 * Completes and validates a distilled static embedding model directory. For WordPiece models, the
 * tool derives {@code vocab.txt} and {@code tokenizer_config.json} from {@code tokenizer.json}.
 */
public class AssembleModelTool extends BasicCmdLineTool {

  /** Command-line parameters accepted by this tool. */
  interface Params extends AssembleModelParams {
  }

  /** {@inheritDoc} */
  @Override
  public String getShortDescription() {
    return "Completes and verifies a distilled static embedding model directory";
  }

  /** {@inheritDoc} */
  @Override
  public String getHelp() {
    return getBasicHelp(Params.class);
  }

  /** {@inheritDoc} */
  @Override
  public void run(String[] args) {
    final Params params = validateAndParseParams(args, Params.class);
    final File modelDir = params.getModelDir();
    if (!modelDir.isDirectory()) {
      throw new TerminateToolException(1,
          "Model directory does not exist or is not a directory: " + modelDir);
    }
    final ModelAssembler.Result result;
    try {
      result = ModelAssembler.assemble(modelDir.toPath());
    } catch (IllegalArgumentException | InvalidFormatException e) {
      throw new TerminateToolException(1, e.getMessage(), e);
    } catch (IOException e) {
      throw new TerminateToolException(-1,
          "IO error while assembling " + modelDir + ": " + e.getMessage(), e);
    }
    if (result.wroteVocabulary()) {
      System.out.println("Wrote vocab.txt derived from tokenizer.json");
    }
    if (result.wroteTokenizerConfig()) {
      System.out.println("Wrote tokenizer_config.json derived from tokenizer.json");
    }
    System.out.println("Assembled and verified a " + result.family() + " model: "
        + result.vocabularySize() + " rows"
        + (result.termCount() > 0 ? " plus " + result.termCount() + " terms" : "")
        + ", dimension " + result.dimension());
  }
}
