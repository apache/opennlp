/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package opennlp.perceptron;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import opennlp.model.PlainTextFileDataReader;

public class PlainTextPerceptronModelReader extends PerceptronModelReader {
  
  /**
   * Constructor which directly instantiates the BufferedReader containing
   * the model contents.
   *
   * @param br The BufferedReader containing the model information.
   */
  public PlainTextPerceptronModelReader(BufferedReader br) {
    super(new PlainTextFileDataReader(br));
  }

  /**
   * Constructor which takes a File and creates a reader for it. Detects
   * whether the file is gzipped or not based on whether the suffix contains
   * ".gz".
   *
   * @param f The File in which the model is stored.
   */
  public PlainTextPerceptronModelReader (File f) throws IOException {
    super(f);
  }
}
