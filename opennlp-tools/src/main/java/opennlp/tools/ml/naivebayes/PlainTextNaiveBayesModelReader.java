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

package opennlp.tools.ml.naivebayes;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import opennlp.tools.ml.model.PlainTextFileDataReader;

/**
 * A {@link NaiveBayesModelReader} that reads models from a plain text format.
 *
 * @see NaiveBayesModelReader
 */
public class PlainTextNaiveBayesModelReader extends NaiveBayesModelReader {
  
  /**
   * Instantiates {@link PlainTextNaiveBayesModelReader} via a {@link BufferedReader}
   * containing the model contents.
   *
   * @param br The {@link BufferedReader} containing the model information.
   *            It must be open and have bytes available.
   */
  public PlainTextNaiveBayesModelReader(BufferedReader br) {
    super(new PlainTextFileDataReader(br));
  }

  /**
   * Instantiates {@link PlainTextNaiveBayesModelReader} via a {@link File} and creates
   * a reader for it. Based on whether the file's suffix contains {@code .gz},
   * it detects whether the file is gzipped or not.
   *
   * @param f The {@link File} that references the model to be read.
   */
  public PlainTextNaiveBayesModelReader(File f) throws IOException {
    super(f);
  }
}
