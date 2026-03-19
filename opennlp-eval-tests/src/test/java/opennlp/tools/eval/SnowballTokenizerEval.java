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
package opennlp.tools.eval;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import opennlp.tools.stemmer.snowball.SnowballStemmer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Eval tests for the {@link SnowballStemmer} class.
 * <p>
 * Uses the data set provided by <a href="https://github.com/snowballstem/snowball-data"></a>
 * to test all languages available in OpenNLP.
 * <p>
 */
public class SnowballTokenizerEval extends AbstractEvalTest {

  @ParameterizedTest
  @EnumSource(SnowballStemmer.ALGORITHM.class)
  public void test(SnowballStemmer.ALGORITHM lang) throws IOException {

    final List<String> vocabulary = getData(lang, "voc.txt");
    final List<String> expectedOutputs = getData(lang, "output.txt");

    assertEquals(vocabulary.size(), expectedOutputs.size(), "Expected equally sized lists.");
    final SnowballStemmer stemmer = new SnowballStemmer(lang);

    for (int i = 0; i < vocabulary.size(); i++) {

      final String word = vocabulary.get(i);
      final String stem = expectedOutputs.get(i);

      assertEquals(stem, stemmer.stem(word));
    }
  }

  private List<String> getData(SnowballStemmer.ALGORITHM lang, String name) throws IOException {
    final Path expectedOutput = getSnowballDataLanguagePath(
        getSnowballDataPath(), lang).resolve(name);
    return Files.readAllLines(expectedOutput);
  }

  private Path getSnowballDataPath() throws FileNotFoundException {
    return getOpennlpDataDir().toPath().resolve("snowball-data");
  }

  private Path getSnowballDataLanguagePath(Path root, SnowballStemmer.ALGORITHM lang) {
    return root.resolve(lang.toString().toLowerCase(Locale.ROOT));
  }

}
