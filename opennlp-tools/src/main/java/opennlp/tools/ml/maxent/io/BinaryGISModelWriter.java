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

package opennlp.tools.ml.maxent.io;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import opennlp.tools.ml.model.AbstractModel;
import opennlp.tools.ml.model.ModelParameterChunker;

/**
 * A {@link GISModelWriter} that writes models in a binary format.
 *
 * @see opennlp.tools.ml.maxent.GISModel
 */
public class BinaryGISModelWriter extends GISModelWriter {
  private final DataOutputStream output;

  /**
   * Instantiates {@link BinaryGISModelWriter} via an
   * {@link AbstractModel GIS model} and a {@link File}.
   * <p>
   * Prepares writing of a {@code model} to the file.
   * Based on whether the file's suffix contains {@code .gz}, it detects whether
   * the file is gzipped or not.
   *
   * @param model The {@link AbstractModel GIS model} which is to be persisted.
   * @param f The {@link File} in which the model is to be persisted.
   *
   * @throws IOException Thrown if IO errors occurred.
   * @see opennlp.tools.ml.maxent.GISModel
   */
  public BinaryGISModelWriter(AbstractModel model, File f) throws IOException {

    super(model);

    if (f.getName().endsWith(".gz")) {
      output = new DataOutputStream(new GZIPOutputStream(
          new FileOutputStream(f)));
    } else {
      output = new DataOutputStream(new FileOutputStream(f));
    }
  }

  /**
   * Instantiates {@link BinaryGISModelWriter} via
   * an {@link AbstractModel GIS model} and a {@link DataOutputStream}.
   *
   * @param model The {@link AbstractModel GIS model} which is to be persisted.
   * @param dos The {@link DataOutputStream} which is used to persist the {@code model}.
   *            The {@code dos} must be opened.
   */
  public BinaryGISModelWriter(AbstractModel model, DataOutputStream dos) {
    super(model);
    output = dos;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void writeUTF(String s) throws java.io.IOException {
    ModelParameterChunker.writeUTF(output, s);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void writeInt(int i) throws java.io.IOException {
    output.writeInt(i);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void writeDouble(double d) throws java.io.IOException {
    output.writeDouble(d);
  }
  
  @Override
  public void close() throws java.io.IOException {
    output.flush();
    output.close();
  }

}
