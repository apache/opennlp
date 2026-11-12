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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.langdetect.LanguageSample;
import opennlp.tools.util.InvalidFormatException;

/**
 * Tests for the {@link LeipzigLanguageSampleStream} class.
 */
public class LeipzigLanguageSampleStreamTest {

  private static final String HIDDEN_FILE = ".hidden";

  @TempDir
  File emptyTempDir;

  @TempDir
  Path samplesDir;

  private String testDataPath;

  @BeforeEach
  void setUp() throws IOException, URISyntaxException {
    copySamples(samplesDir);
    testDataPath = samplesDir.toString();
  }

  /**
   * Copies the Leipzig samples to {@code target} and adds a hidden file with valid sentences,
   * which the stream must skip. The hidden file is created here because source releases
   * do not contain dotfiles.
   */
  static void copySamples(Path target) throws IOException, URISyntaxException {
    Path source = Paths.get(Objects.requireNonNull(LeipzigLanguageSampleStreamTest.class
        .getClassLoader().getResource("opennlp/tools/formats/leipzig/samples")).toURI());
    try (Stream<Path> paths = Files.walk(source)) {
      for (Path path : (Iterable<Path>) paths::iterator) {
        Path copy = target.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(copy);
        } else {
          Files.copy(path, copy);
        }
      }
    }
    Path hidden = Files.writeString(target.resolve(HIDDEN_FILE),
        "1\tThis hidden file must not be read.\n2\tNeither must this sentence.\n",
        StandardCharsets.UTF_8);
    // A dotfile is hidden on Unix; Windows needs the DOS hidden attribute.
    DosFileAttributeView dos = Files.getFileAttributeView(hidden, DosFileAttributeView.class);
    if (dos != null) {
      dos.setHidden(true);
    }
    Assertions.assertTrue(Files.isHidden(hidden));
  }

  @Test
  void testReadSentenceFiles() {
    int samplesPerLanguage = 2;
    int sentencesPerSample = 1;
    try (LeipzigLanguageSampleStream stream = new LeipzigLanguageSampleStream(new File(testDataPath),
            sentencesPerSample, samplesPerLanguage)) {
      
      int count = 0;
      while (stream.read() != null) {
        count++;
      }

      Assertions.assertEquals(4, count);

    } catch (IOException e) {
      Assertions.fail();
    }
  }

  @Test
  void testSkipHiddenFiles() throws IOException {
    Set<String> languages = new TreeSet<>();
    try (LeipzigLanguageSampleStream stream = new LeipzigLanguageSampleStream(new File(testDataPath),
            1, 2)) {
      LanguageSample sample;
      while ((sample = stream.read()) != null) {
        languages.add(sample.language().getLang());
      }
    }
    Assertions.assertTrue(Files.exists(samplesDir.resolve(HIDDEN_FILE)));
    Assertions.assertEquals(Set.of("dan", "eng"), languages);
  }

  @Test
  void testReadSentenceFilesTwice() throws IOException {
    Assertions.assertEquals(countSamples(), countSamples());
  }

  private int countSamples() throws IOException {
    try (LeipzigLanguageSampleStream stream = new LeipzigLanguageSampleStream(new File(testDataPath),
            1, 2)) {
      int count = 0;
      while (stream.read() != null) {
        count++;
      }
      return count;
    }
  }

  @Test
  void testNotEnoughSentences() {
    Assertions.assertThrows(InvalidFormatException.class, () -> {
      int samplesPerLanguage = 2;
      int sentencesPerSample = 2;

      try (LeipzigLanguageSampleStream stream = new LeipzigLanguageSampleStream(
              new File(testDataPath), sentencesPerSample, samplesPerLanguage)) {

        while (stream.read() != null) ;
      }

    });
  }

  @Test
  void testReadSentenceFilesWithEmptyDir() {
    int samplesPerLanguage = 2;
    int sentencesPerSample = 1;
    try (LeipzigLanguageSampleStream stream = new LeipzigLanguageSampleStream(
            emptyTempDir, sentencesPerSample, samplesPerLanguage)) {

      int count = 0;
      while (stream.read() != null) {
        count++;
      }
      Assertions.assertEquals(0, count);
    } catch (IOException e) {
      Assertions.fail();
    }
  }

}
