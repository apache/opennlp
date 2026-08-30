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
import java.util.Locale;

import opennlp.embeddings.ModelQuantizer;
import opennlp.tools.cmdline.BasicCmdLineTool;
import opennlp.tools.cmdline.TerminateToolException;

/**
 * Quantizes a static embedding model directory's matrix to 2-4 bits per dimension, writing
 * {@code model.quantized} next to the {@code model.safetensors}, and prints the sizes and the
 * measured reconstruction quality. {@code StaticEmbeddingModel.load} prefers the quantized file
 * from then on; the safetensors may be deleted for a slim deployment.
 */
public class QuantizeModelTool extends BasicCmdLineTool {

  interface Params extends QuantizeModelParams {
  }

  @Override
  public String getShortDescription() {
    return "Quantizes a static embedding model directory's matrix to 2-4 bits per dimension";
  }

  @Override
  public String getHelp() {
    return getBasicHelp(Params.class);
  }

  @Override
  public void run(String[] args) {
    final Params params = validateAndParseParams(args, Params.class);
    final ModelQuantizer.Result result;
    try {
      result = ModelQuantizer.quantize(params.getModelDir().toPath(), params.getBits(),
          params.getSeed());
    } catch (IllegalArgumentException e) {
      throw new TerminateToolException(1, e.getMessage());
    } catch (IOException e) {
      throw new TerminateToolException(-1,
          "IO error while quantizing " + params.getModelDir() + ": " + e.getMessage(), e);
    }
    System.out.println("Quantized " + result.rowCount() + " rows of dimension "
        + result.dimension() + " to " + result.bits() + " bits"
        + (result.hasWeights() ? ", carrying the per-token weights" : ""));
    System.out.println(String.format(Locale.ROOT,
        "Size: %,d bytes safetensors, %,d bytes quantized (%.1fx smaller)",
        result.safetensorsBytes(), result.quantizedBytes(),
        result.safetensorsBytes() / (double) result.quantizedBytes()));
    System.out.println(String.format(Locale.ROOT,
        "Verified from disk: mean cosine %.4f between original and reconstructed rows "
            + "(%d sampled)", result.meanCosine(), result.sampledRows()));
  }
}
