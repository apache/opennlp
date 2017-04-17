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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class MockInputStreamFactory implements InputStreamFactory {

  private final File inputSourceFile;
  private final String inputSourceStr;
  private final Charset charset;

  public MockInputStreamFactory(File file) {
    this.inputSourceFile = file;
    this.inputSourceStr = null;
    this.charset = null;
  }

  public MockInputStreamFactory(String str) {
    this(str, StandardCharsets.UTF_8);
  }

  public MockInputStreamFactory(String str, Charset charset) {
    this.inputSourceFile = null;
    this.inputSourceStr = str;
    this.charset = charset;
  }

  @Override
  public InputStream createInputStream() throws IOException {
    if (inputSourceFile != null) {
      return getClass().getClassLoader().getResourceAsStream(inputSourceFile.getPath());
    }
    else {
      return new ByteArrayInputStream(inputSourceStr.getBytes(charset));
    }
  }
}
