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
 * The command-line arguments of {@link EvalVectorSearchTool}.
 */
interface EvalVectorSearchParams {

  /**
   * {@return the model directory to evaluate}
   */
  @ParameterDescription(valueName = "dir",
      description = "The static embedding model directory to evaluate.")
  String getModel();

  /**
   * {@return the normalized passages file}
   */
  @ParameterDescription(valueName = "file",
      description = "The passages JSONL file to index and query.")
  String getPassages();

  /**
   * {@return the normalized dictionary file}
   */
  @ParameterDescription(valueName = "file",
      description = "The dictionary TSV file for the definition-to-headword evaluation.")
  String getDictionary();

  /**
   * {@return the markdown report file to write}
   */
  @ParameterDescription(valueName = "file",
      description = "The markdown report to write; a TSV with the same metrics is written "
          + "next to it with the extension .tsv.")
  String getOut();

  /**
   * {@return the quantization bit width}
   */
  @OptionalParameter(defaultValue = "4")
  @ParameterDescription(valueName = "num",
      description = "The quantization bit width, 2 to 4, default is 4.")
  Integer getBits();

  /**
   * {@return the quantization rotation seed}
   */
  @OptionalParameter(defaultValue = "42")
  @ParameterDescription(valueName = "num",
      description = "The quantization rotation seed, default is 42.")
  Long getSeed();

  /**
   * {@return the evaluation depth}
   */
  @OptionalParameter(defaultValue = "10")
  @ParameterDescription(valueName = "num",
      description = "The evaluation depth k for recall and MRR, default is 10.")
  Integer getTopK();
}
