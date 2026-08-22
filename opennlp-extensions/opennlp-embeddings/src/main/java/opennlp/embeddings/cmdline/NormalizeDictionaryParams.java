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

import opennlp.tools.cmdline.ArgumentParser.ParameterDescription;

/**
 * The command-line arguments of {@link NormalizeDictionaryTool}.
 */
interface NormalizeDictionaryParams {

  /**
   * {@return the directory holding the raw Bouvier per-letter HTML files}
   */
  @ParameterDescription(valueName = "dir",
      description = "the directory holding the raw Bouvier per-letter .htm files")
  File getRawDir();

  /**
   * {@return the dictionary TSV file to write}
   */
  @ParameterDescription(valueName = "file",
      description = "the dictionary.tsv file to write, one HEADWORD<TAB>definition per line")
  File getOut();
}
