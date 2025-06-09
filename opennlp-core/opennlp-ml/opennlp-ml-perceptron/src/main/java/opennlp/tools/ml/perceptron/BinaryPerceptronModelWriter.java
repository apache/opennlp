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

package opennlp.tools.ml.perceptron;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.ModelParameterChunker;

/**
 * A {@link PerceptronModelWriter} that writes models in a binary format.
 */
public class BinaryPerceptronModelWriter extends PerceptronModelWriter {

  private final DataOutputStream output;

  /**
   * Instantiates {@link BinaryPerceptronModelWriter} via an {@link AbstractModel perceptron model}
   * and a {@link File}. Prepares writing of the {@code model} to the file.
   * Based on whether the file's suffix contains {@code .gz}, it detects whether
   * the file is gzipped or not.
   *
   * @param model The {@link AbstractModel perceptron model} which is to be persisted.
   * @param f The {@link File} in which the model is to be persisted.
   *
   * @see PerceptronModel
   */
  public BinaryPerceptronModelWriter(AbstractModel model, File f) throws IOException {
    super(model);

    if (f.getName().endsWith(".gz")) {
      output = new DataOutputStream(
          new GZIPOutputStream(new FileOutputStream(f)));
    }
    else {
      output = new DataOutputStream(new FileOutputStream(f));
    }
  }

  /**
   * Instantiates {@link BinaryPerceptronModelWriter} via an {@link AbstractModel perceptron model}
   * and a {@link DataOutputStream}. Prepares writing a {@code model} to the file.
   * Based on whether the file's suffix contains {@code .gz}, it detects whether
   * the file is gzipped or not.
   *
   * @param model The {@link AbstractModel perceptron model} which is to be persisted.
   * @param dos The {@link DataOutputStream} which is used to persist the {@code model}.
   *            The {@code dos} must be opened.
   */
  public BinaryPerceptronModelWriter(AbstractModel model, DataOutputStream dos) {
    super(model);
    output = dos;
  }

  @Override
  public void writeUTF(String s) throws IOException {
    ModelParameterChunker.writeUTF(output, s);
  }

  @Override
  public void writeInt(int i) throws IOException {
    output.writeInt(i);
  }

  @Override
  public void writeDouble(double d) throws IOException {
    output.writeDouble(d);
  }

  @Override
  public void close() throws IOException {
    output.flush();
    output.close();
  }

}
