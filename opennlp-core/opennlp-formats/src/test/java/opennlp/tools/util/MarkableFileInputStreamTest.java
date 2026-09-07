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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MarkableFileInputStreamTest {

  @TempDir
  Path tempDir;

  private Path file() throws IOException {
    Path file = tempDir.resolve("lines.txt");
    Files.writeString(file, "first\nsecond\n", StandardCharsets.UTF_8);
    return file;
  }

  @Test
  void testMarkAndReset() throws IOException {
    try (InputStream in = new MarkableFileInputStreamFactory(file().toFile()).createInputStream()) {
      Assertions.assertTrue(in.markSupported());
      Assertions.assertEquals('f', in.read());
      in.mark(0);
      Assertions.assertEquals('i', in.read());
      in.reset();
      Assertions.assertEquals('i', in.read());
    }
  }

  @Test
  void testResetWithoutMarkIsRejected() throws IOException {
    try (InputStream in = new MarkableFileInputStreamFactory(file().toFile()).createInputStream()) {
      Assertions.assertThrows(IOException.class, in::reset);
    }
  }

  @Test
  void testCloseClosesTheFile() throws IOException {
    InputStream in = new MarkableFileInputStreamFactory(file().toFile()).createInputStream();
    Assertions.assertEquals('f', in.read());
    in.close();
    Assertions.assertThrows(IOException.class, in::read, "reading a closed stream must fail");
  }

  @Test
  void testCloseThroughReaderClosesTheFile() throws IOException {
    InputStream in = new MarkableFileInputStreamFactory(file().toFile()).createInputStream();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      Assertions.assertEquals("first", reader.readLine());
    }
    Assertions.assertThrows(IOException.class, in::read, "the reader must close the file");
  }
}
