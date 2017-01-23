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

public class PlainTextFileDataReader implements DataReader {

  private BufferedReader input;

  public PlainTextFileDataReader(File f) throws IOException {
    if (f.getName().endsWith(".gz")) {
      input = new BufferedReader(new InputStreamReader(new BufferedInputStream(
          new GZIPInputStream(new BufferedInputStream(new FileInputStream(f))))));
    }
    else {
      input = new BufferedReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(f))));
    }
  }

  public PlainTextFileDataReader(InputStream in) {
    input = new BufferedReader(new InputStreamReader(in));
  }

  public PlainTextFileDataReader(BufferedReader in) {
    input = in;
  }

  public double readDouble() throws IOException {
    return Double.parseDouble(input.readLine());
  }

  public int readInt() throws IOException {
    return Integer.parseInt(input.readLine());
  }

  public String readUTF() throws IOException {
    return input.readLine();
  }

}
