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

package opennlp.tools.ml.model;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * A {@link DataReader} that reads files from a binary format.
 */
public class BinaryFileDataReader implements DataReader {

  private final DataInputStream input;

  /**
   * Instantiates {@link BinaryFileDataReader} via a {@link File} and creates
   * a {@link DataInputStream} for it.
   * Based on whether the file's suffix contains {@code .gz},
   * it detects whether the file is gzipped or not.
   *
   * @param f The {@link File} that references the model to be read.
   */
  public BinaryFileDataReader(File f) throws IOException {
    if (f.getName().endsWith(".gz")) {
      input = new DataInputStream(new BufferedInputStream(
          new GZIPInputStream(new BufferedInputStream(new FileInputStream(f)))));
    }
    else {
      input = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
    }
  }

  /**
   * Instantiates {@link BinaryFileDataReader} via an {@link InputStream} and creates
   * a {@link DataInputStream} for it.
   *
   * @param in The {@link InputStream} that references the model to be read.
   */
  public BinaryFileDataReader(InputStream in) {
    this(new DataInputStream(in));
  }

  /**
   * Instantiates {@link BinaryFileDataReader} via an {@link DataInputStream}.
   *
   * @param in The {@link DataInputStream} that references the model to be read.
   */
  public BinaryFileDataReader(DataInputStream in) {
    input = in;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double readDouble() throws IOException {
    return input.readDouble();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int readInt() throws IOException {
    return input.readInt();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String readUTF() throws IOException {
    return ModelParameterChunker.readUTF(input);
  }

}
