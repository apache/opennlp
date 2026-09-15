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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.langdetect.LanguageSample;
import opennlp.tools.util.InvalidFormatException;

/**
 * Tests for the {@link LeipzigLanguageSampleStream} class.
 */

public class LeipzigLanguageSampleStreamTest {

  private static final String testDataPath = LeipzigLanguageSampleStreamTest.class
      .getClassLoader().getResource("opennlp/tools/formats/leipzig/samples").getPath();

  @TempDir
  File emptyTempDir;

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

  @Test
  void testOnlyFilesWithLowerCaseAsciiLanguageCodesAreRead() throws IOException {
    // Leipzig corpus file names start with the three-letter language code
    String[] names = {"eng_news_2010_10K-sentences.txt", "deu_wikipedia_2016_10K-sentences.txt",
        "Eng_news_2010_10K-sentences.txt", "ENG_news_2010_10K-sentences.txt",
        "enG_news_2010_10K-sentences.txt", "en1_news_2010_10K-sentences.txt",
        "e-g_news_2010_10K-sentences.txt", "en_news_2010_10K-sentences.txt",
        "\u00E9ng_news_2010_10K-sentences.txt", "\u0130ng_news_2010_10K-sentences.txt",
        "\uFF45ng_news_2010_10K-sentences.txt", "\uD835\uDC1Abc_news_2010_10K-sentences.txt", "en"};
    for (String name : names) {
      Files.writeString(new File(emptyTempDir, name).toPath(),
          "1\tThis is a sentence.\n2\tThis is another sentence.\n", StandardCharsets.UTF_8);
    }
    List<String> languages = new ArrayList<>();
    try (LeipzigLanguageSampleStream stream = new LeipzigLanguageSampleStream(emptyTempDir, 1, 2)) {
      LanguageSample sample;
      while ((sample = stream.read()) != null) {
        languages.add(sample.language().getLang());
      }
    }
    Assertions.assertEquals(List.of("deu", "deu", "eng", "eng"), languages);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\n", "\r\n", "\r"})
  void testSentenceFilesWithEveryLineTerminatorAreRead(String terminator) throws IOException {
    Files.writeString(new File(emptyTempDir, "eng-sentences.txt").toPath(),
        "1\tFirst sentence." + terminator + "2\tSecond sentence." + terminator, StandardCharsets.UTF_8);
    List<String> texts = new ArrayList<>();
    try (LeipzigLanguageSampleStream stream = new LeipzigLanguageSampleStream(emptyTempDir, 1, 2)) {
      LanguageSample sample;
      while ((sample = stream.read()) != null) {
        texts.add(sample.context().toString());
      }
    }
    Collections.sort(texts);
    Assertions.assertEquals(List.of("First sentence. ", "Second sentence. "), texts);
  }
}
