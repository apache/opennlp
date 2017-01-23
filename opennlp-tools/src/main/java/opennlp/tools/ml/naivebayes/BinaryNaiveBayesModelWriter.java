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

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import opennlp.tools.ml.model.AbstractModel;

/**
 * Model writer that saves models in binary format.
 */
public class BinaryNaiveBayesModelWriter extends NaiveBayesModelWriter {
  private DataOutputStream output;

  /**
   * Constructor which takes a NaiveBayesModel and a File and prepares itself to
   * write the model to that file. Detects whether the file is gzipped or not
   * based on whether the suffix contains ".gz".
   *
   * @param model The NaiveBayesModel which is to be persisted.
   * @param f     The File in which the model is to be persisted.
   */
  public BinaryNaiveBayesModelWriter(AbstractModel model, File f) throws IOException {

    super(model);

    if (f.getName().endsWith(".gz")) {
      output = new DataOutputStream(
          new GZIPOutputStream(new FileOutputStream(f)));
    } else {
      output = new DataOutputStream(new FileOutputStream(f));
    }
  }

  /**
   * Constructor which takes a NaiveBayesModel and a DataOutputStream and prepares
   * itself to write the model to that stream.
   *
   * @param model The NaiveBayesModel which is to be persisted.
   * @param dos   The stream which will be used to persist the model.
   */
  public BinaryNaiveBayesModelWriter(AbstractModel model, DataOutputStream dos) {
    super(model);
    output = dos;
  }

  public void writeUTF(String s) throws java.io.IOException {
    output.writeUTF(s);
  }

  public void writeInt(int i) throws java.io.IOException {
    output.writeInt(i);
  }

  public void writeDouble(double d) throws java.io.IOException {
    output.writeDouble(d);
  }

  public void close() throws java.io.IOException {
    output.flush();
    output.close();
  }

}
