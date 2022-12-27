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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

/**
 * A generic {@link DataReader} implementation for plain text files.
 *
 * @see DataReader
 */
public class PlainTextFileDataReader implements DataReader {

  private final BufferedReader input;

  /**
   * Initializes a {@link PlainTextFileDataReader} via a {@link File}.
   *
   * @param f The {@link File} that references the model to be read.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public PlainTextFileDataReader(File f) throws IOException {
    if (f.getName().endsWith(".gz")) {
      input = new BufferedReader(new InputStreamReader(new BufferedInputStream(
          new GZIPInputStream(new BufferedInputStream(new FileInputStream(f))))));
    }
    else {
      input = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(f))));
    }
  }

  /**
   * Initializes a {@link PlainTextFileDataReader} via a {@link InputStream}.
   *
   * @param in The {@link InputStream} that references the file to be read.
   */
  public PlainTextFileDataReader(InputStream in) {
    input = new BufferedReader(new InputStreamReader(in));
  }

  /**
   * Initializes a {@link PlainTextFileDataReader} via a {@link BufferedReader}.
   *
   * @param in The {@link BufferedReader} that references the file to be read.
   */
  public PlainTextFileDataReader(BufferedReader in) {
    input = in;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double readDouble() throws IOException {
    return Double.parseDouble(input.readLine());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int readInt() throws IOException {
    return Integer.parseInt(input.readLine());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String readUTF() throws IOException {
    return input.readLine();
  }

}
