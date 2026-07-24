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

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;

/**
 * The command-line arguments of {@link QuantizeModelTool}.
 */
interface QuantizeModelParams {

  /**
   * {@return the model directory whose safetensors matrix is quantized in place}
   */
  @ParameterDescription(valueName = "dir",
      description = "the model directory whose model.safetensors is quantized in place")
  File getModelDir();

  /**
   * {@return the bit width per dimension}
   */
  @ParameterDescription(valueName = "bits",
      description = "bits per dimension, 2 to 4; fewer bits, smaller file, lower fidelity")
  @OptionalParameter(defaultValue = "4")
  Integer getBits();

  /**
   * {@return the rotation seed; the same matrix, bits, and seed write the same file}
   */
  @ParameterDescription(valueName = "seed",
      description = "the rotation seed; the same matrix, bits, and seed write the same file")
  @OptionalParameter(defaultValue = "0")
  Integer getSeed();
}
