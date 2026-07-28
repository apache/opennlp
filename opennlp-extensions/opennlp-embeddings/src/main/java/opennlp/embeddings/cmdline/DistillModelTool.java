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

import java.io.IOException;
import java.nio.file.Path;

import opennlp.embeddings.ModelDistiller;
import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.TerminateToolException;

/**
 * Distills a sentence-transformer teacher into a static embedding model directory, the
 * {@code opennlp-embeddings DistillModel} command. This is Model2Vec's distillation
 * (teacher forward pass over the vocabulary, PCA, Zipf weighting) in Java, so producing a table
 * no longer needs a Python environment; see {@link ModelDistiller} for the pipeline.
 *
 * <p>The teacher is a Hugging Face model id (its files download once into a local cache) or a
 * local directory holding {@code tokenizer.json} and {@code onnx/model.onnx}. A SentencePiece
 * teacher also needs its trained {@code .model} file, downloaded or supplied alongside. The
 * written directory is completed and verified by loading it, so a run that prints a summary is a
 * directory that works.</p>
 */
public class DistillModelTool extends BasicCmdLineTool {

  interface Params extends DistillModelParams {
  }

  @Override
  public String getShortDescription() {
    return "Distills a sentence-transformer teacher into a static embedding model";
  }

  @Override
  public String getHelp() {
    return getBasicHelp(Params.class);
  }

  @Override
  public void run(String[] args) {
    final Params params = validateAndParseParams(args, Params.class);
    if (params.getTeacher() == null) {
      throw new TerminateToolException(1, "The -teacher parameter is required: a Hugging Face "
          + "model id (org/model) or a local teacher directory");
    }
    if (params.getOut() == null) {
      throw new TerminateToolException(1, "The -out parameter is required: the model directory "
          + "to write");
    }
    final ModelDistiller.ProgressListener listener = System.out::println;
    final ModelDistiller.Result result;
    try {
      result = ModelDistiller.distill(params.getTeacher(), Path.of(params.getOut()),
          params.getPcaDims(), listener);
    } catch (IllegalArgumentException e) {
      throw new TerminateToolException(1, e.getMessage());
    } catch (IOException e) {
      throw new TerminateToolException(-1,
          "IO error while distilling: " + e.getMessage(), e);
    }
    System.out.println("Distilled and verified a " + result.family() + " model: "
        + result.vocabularySize() + " rows, " + result.teacherDimension() + "d -> "
        + result.dimension() + "d, PCA kept "
        + String.format("%.1f", result.explainedVarianceRatio() * 100) + "% of the variance");
  }
}
