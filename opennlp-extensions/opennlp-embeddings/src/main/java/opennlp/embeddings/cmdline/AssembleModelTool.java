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

/**
 * Completes a distilled embedding model directory so {@code StaticEmbeddingModel.load} can open it,
 * then verifies it by loading it.
 *
 * <p>A Model2Vec distillation writes {@code model.safetensors}, {@code tokenizer.json}, and
 * {@code config.json}. For a WordPiece model this tool derives the missing {@code vocab.txt} and
 * {@code tokenizer_config.json} from {@code tokenizer.json}. For a SentencePiece model it checks
 * that the trained {@code .model} file, which comes from the teacher, is present, and it names the
 * fix if it is not. Either way it loads the assembled directory and prints its family, dimension,
 * and vocabulary size, so a run that prints a summary is a directory that works.</p>
 */
public class AssembleModelTool extends BasicCmdLineTool {

  interface Params extends AssembleModelParams {
  }

  @Override
  public String getShortDescription() {
    return "Completes and verifies a distilled static embedding model directory";
  }

  @Override
  public String getHelp() {
    return getBasicHelp(Params.class);
  }

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
    } catch (IllegalArgumentException e) {
      throw new TerminateToolException(1, e.getMessage());
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
        + result.vocabularySize() + " rows, dimension " + result.dimension());
  }
}
