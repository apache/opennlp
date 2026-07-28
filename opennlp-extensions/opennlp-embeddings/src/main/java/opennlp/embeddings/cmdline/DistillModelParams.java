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

import opennlp.tools.cmdline.ArgumentParser.OptionalParameter;
import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;

/**
 * The command-line arguments of {@link DistillModelTool}.
 */
interface DistillModelParams {

  /**
   * {@return the teacher to distill: a local directory or a Hugging Face model id}
   */
  @ParameterDescription(valueName = "hf-id-or-path",
      description = "the sentence-transformer teacher: a Hugging Face model id (org/model) or a "
          + "local directory holding tokenizer.json and onnx/model.onnx")
  String getTeacher();

  /**
   * {@return the model directory to write}
   */
  @ParameterDescription(valueName = "dir",
      description = "the output directory for the distilled static embedding model")
  String getOut();

  /**
   * {@return the number of PCA dimensions to keep}
   */
  @OptionalParameter(defaultValue = "256")
  @ParameterDescription(valueName = "n",
      description = "the number of principal components to keep (default: 256)")
  Integer getPcaDims();
}
