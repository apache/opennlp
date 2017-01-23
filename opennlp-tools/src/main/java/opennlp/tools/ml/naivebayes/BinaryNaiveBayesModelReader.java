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

public class BinaryNaiveBayesModelReader extends NaiveBayesModelReader {


  /**
   * Constructor which directly instantiates the DataInputStream containing
   * the model contents.
   *
   * @param dis The DataInputStream containing the model information.
   */
  public BinaryNaiveBayesModelReader(DataInputStream dis) {
    super(new BinaryFileDataReader(dis));
  }

  /**
   * Constructor which takes a File and creates a reader for it. Detects
   * whether the file is gzipped or not based on whether the suffix contains
   * ".gz"
   *
   * @param f The File in which the model is stored.
   */
  public BinaryNaiveBayesModelReader(File f) throws IOException {
    super(f);
  }
}
