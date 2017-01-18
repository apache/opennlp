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

package opennlp.morfologik.builder;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Properties;

import morfologik.stemming.DictionaryMetadata;
import morfologik.stemming.EncoderType;
import morfologik.tools.DictCompile;

/**
 * Utility class to build Morfologik dictionaries from a tab separated values
 * file. The first column is the word, the second its lemma and the third a POS
 * tag. If there is no lemma information leave the second column empty.
 */
public class MorfologikDictionayBuilder {

  /**
   * Helper to compile a morphological dictionary automaton.
   *
   * @param input       The input file (base,inflected,tag). An associated metadata
   *                    (*.info) file must exist.
   * @param overwrite   Overwrite the output file if it exists.
   * @param validate    Validate input to make sure it makes sense.
   * @param acceptBom   Accept leading BOM bytes (UTF-8).
   * @param acceptCr    Accept CR bytes in input sequences (\r).
   * @param ignoreEmpty Ignore empty lines in the input.
   * @return the dictionary path
   * @throws Exception
   */
  public Path build(Path input, boolean overwrite, boolean validate,
                    boolean acceptBom, boolean acceptCr, boolean ignoreEmpty)
      throws Exception {

    DictCompile compiler = new DictCompile(input, overwrite, validate, acceptBom,
        acceptCr, ignoreEmpty);
    compiler.call();

    Path metadataPath = DictionaryMetadata.getExpectedMetadataLocation(input);

    return metadataPath.resolveSibling(
        metadataPath.getFileName().toString().replaceAll(
            "\\." + DictionaryMetadata.METADATA_FILE_EXTENSION + "$", ".dict"));
  }

  /**
   * Helper to compile a morphological dictionary automaton using default
   * parameters.
   *
   * @param input The input file (base,inflected,tag). An associated metadata
   *              (*.info) file must exist.
   * @return the dictionary path
   * @throws Exception
   */
  public Path build(Path input) throws Exception {
    return build(input, true, true, false, false, false);
  }

  Properties createProperties(Charset encoding, String separator, EncoderType encoderType)
      throws IOException {
    Properties properties = new Properties();
    properties.setProperty("fsa.dict.separator", separator);
    properties.setProperty("fsa.dict.encoding", encoding.name());
    properties.setProperty("fsa.dict.encoder", encoderType.name());
    return properties;
  }
}
