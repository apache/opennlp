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

package opennlp.tools.formats.convert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

public class FileToStringSampleStream extends FilterObjectStream<File, String> {

  private final Charset encoding;

  public FileToStringSampleStream(ObjectStream<File> samples, Charset encoding) {
    super(samples);

    this.encoding = encoding;
  }

  private static String readFile(File textFile, Charset encoding) throws IOException {

    Reader in = new BufferedReader(new InputStreamReader(new FileInputStream(textFile), encoding));

    StringBuilder text = new StringBuilder();

    try {
      char buffer[] = new char[1024];
      int length;
      while ((length = in.read(buffer, 0, buffer.length)) > 0) {
        text.append(buffer, 0, length);
      }
    }
    finally {
      try {
        in.close();
      }
      catch (IOException e) {
        // sorry that this can fail!
      }
    }

    return text.toString();
  }

  public String read() throws IOException {

    File sampleFile = samples.read();

    if (sampleFile != null) {
      return readFile(sampleFile, encoding);
    }
    else {
      return null;
    }
  }
}
