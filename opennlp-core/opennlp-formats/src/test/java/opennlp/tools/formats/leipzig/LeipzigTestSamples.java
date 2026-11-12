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

package opennlp.tools.formats.leipzig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.stream.Stream;

/**
 * Provides a copy of the Leipzig test samples together with a hidden sentences file.
 */
final class LeipzigTestSamples {

  /** The name of the hidden sentences file. */
  static final String HIDDEN_FILE_NAME = ".hidden";

  private static final String SAMPLES = "opennlp/tools/formats/leipzig/samples";

  private LeipzigTestSamples() {
  }

  /**
   * Copies the Leipzig test samples into a directory and creates a hidden file with valid
   * sentences next to them. The hidden file yields samples if a reader does not skip it.
   *
   * @param target The directory to copy the samples into. Must exist.
   * @return The path of the hidden file.
   * @throws IOException Thrown if the samples cannot be copied.
   */
  static Path copyWithHiddenFile(Path target) throws IOException {
    Path source;
    try {
      source = Path.of(LeipzigTestSamples.class.getClassLoader().getResource(SAMPLES).toURI());
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
    try (Stream<Path> paths = Files.walk(source)) {
      paths.forEach(path -> {
        Path copy = target.resolve(source.relativize(path).toString());
        try {
          if (Files.isDirectory(path)) {
            Files.createDirectories(copy);
          } else {
            Files.copy(path, copy);
          }
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }

    Path hidden = Files.writeString(target.resolve(HIDDEN_FILE_NAME),
        "1\tThis sentence is hidden.\n2\tThis sentence is hidden too.\n", StandardCharsets.UTF_8);
    // A leading dot hides a file on Unix; Windows needs the DOS attribute.
    DosFileAttributeView dos = Files.getFileAttributeView(hidden, DosFileAttributeView.class);
    if (dos != null) {
      dos.setHidden(true);
    }
    return hidden;
  }
}
