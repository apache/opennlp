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

package opennlp.tools.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Reads a plain text file and returns each line as a {@link String} object.
 */
public class PlainTextByLineStream implements ObjectStream<String> {

  private final Charset encoding;

  private final InputStreamFactory inputStreamFactory;

  private BufferedReader in;

  /**
   * Initializes a {@link PlainTextByLineStream}.
   *
   * @param inputStreamFactory The {@link InputStreamFactory} to use. Must not be {@code null}.
   * @param charsetName The name of the {@link Charset} that is used for interpreting characters.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public PlainTextByLineStream(InputStreamFactory inputStreamFactory, String charsetName)
          throws IOException {
    this(inputStreamFactory, Charset.forName(charsetName));
  }

  /**
   * Initializes a {@link PlainTextByLineStream}.
   *
   * @param inputStreamFactory The {@link InputStreamFactory} to use. Must not be {@code null}.
   * @param charset The {@link Charset} that is used for interpreting characters.
   *
   * @throws IOException Thrown if IO errors occurred.
   */
  public PlainTextByLineStream(InputStreamFactory inputStreamFactory, Charset charset)
          throws IOException {
    this.inputStreamFactory = Objects.requireNonNull(
            inputStreamFactory, "inputStreamFactory must not be null!");
    this.encoding = charset;

    reset();
  }

  @Override
  public String read() throws IOException {
    return in.readLine();
  }

  @Override
  public void reset() throws IOException {

    in = new BufferedReader(
            new InputStreamReader(inputStreamFactory.createInputStream(), encoding));
  }

  @Override
  public void close() throws IOException {
    if (in != null) {
      in.close();
    }
  }
}
