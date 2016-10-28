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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import opennlp.tools.util.FilterObjectStream;
import opennlp.tools.util.ObjectStream;

public class FileToByteArraySampleStream extends FilterObjectStream<File, byte[]> {

  public FileToByteArraySampleStream(ObjectStream<File> samples) {
    super(samples);
  }

  private static byte[] readFile(File file) throws IOException {

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
      byte buffer[] = new byte[1024];
      int length;
      while ((length = in.read(buffer, 0, buffer.length)) > 0) {
        bytes.write(buffer, 0, length);
      }
    }

    return bytes.toByteArray();
  }

  public byte[] read() throws IOException {

    File sampleFile = samples.read();

    if (sampleFile != null) {
      return readFile(sampleFile);
    }
    else {
      return null;
    }
  }
}
