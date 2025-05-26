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

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

import opennlp.tools.ml.model.BinaryFileDataReader;

/**
 * A {@link NaiveBayesModelReader} that reads models from a binary format.
 */
public class BinaryNaiveBayesModelReader extends NaiveBayesModelReader {


  /**
   * Instantiates {@link BinaryNaiveBayesModelReader} via a {@link DataInputStream}
   * containing the model contents.
   *
   * @param dis The {@link DataInputStream} containing the model information.
   *            It must be open and have bytes available.
   */
  public BinaryNaiveBayesModelReader(DataInputStream dis) {
    super(new BinaryFileDataReader(dis));
  }

  /**
   * Instantiates {@link BinaryNaiveBayesModelReader} via a {@link File} and creates
   * a reader for it. Based on whether the file's suffix contains {@code .gz},
   * it detects whether the file is gzipped or not.
   *
   * @param f The {@link File} that references the model to be read.
   */
  public BinaryNaiveBayesModelReader(File f) throws IOException {
    super(f);
  }
}
