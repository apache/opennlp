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

package opennlp.tools.cmdline.languagemodel;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.AbstractLoggerTest;
import opennlp.tools.cmdline.PerformanceMonitor;
import opennlp.tools.cmdline.TerminateToolException;
import opennlp.tools.ngram.NGramGenerator;
import opennlp.tools.ngram.NGramModel;
import opennlp.tools.util.StringList;

class NGramLanguageModelToolTest extends AbstractLoggerTest {

  private static final int NGRAM_MIN_LENGTH = 1;
  private static final int NGRAM_MAX_LENGTH = 3;

  @TempDir
  private static File testDir;

  private static final InputStream sysInputStream = System.in;

  @BeforeAll
  public static void prepare() {
    prepare(LOGGER_OPENNLP);
  }

  @ParameterizedTest
  @MethodSource("provideNgramDictionaryXML")
  void testRunTool(String dataFileName, String[][] providedAndPredictedTokens) {

    try (LogCaptor logCaptorNglmTool = LogCaptor.forClass(NGramLanguageModelTool.class);
         LogCaptor logCaptorPerfMon = LogCaptor.forClass(PerformanceMonitor.class)) {

      //Get test input-file.
      File inputData = new File(testDir, String.format(dataFileName));

      //Configure input stream to provide user-input.
      StringBuilder userInput = new StringBuilder();
      for (String[] item : providedAndPredictedTokens) {
        userInput.append(item[0]).append("\n");
      }
      System.setIn(new ByteArrayInputStream(userInput.toString().getBytes()));

      //Invoke the tool.
      NGramLanguageModelTool nGramLMTool = new NGramLanguageModelTool();
      nGramLMTool.run(new String[] {inputData.getPath()});

      //Collect any LogStream Events generated via the tool.
      List<String> actual = new LinkedList<>();
      actual.addAll(logCaptorNglmTool.getInfoLogs());
      actual.addAll(logCaptorPerfMon.getInfoLogs());


      List<Executable> assertions = new LinkedList<>();

      //assert the expected and actual values of predicted next token for equality.
      for (String[] item : providedAndPredictedTokens) {
        assertions.add(() -> Assertions.assertTrue(actual.stream()
            .filter(l -> l.contains(String.join(", ", item[0].split(" "))))
            .findFirst().orElseThrow(AssertionError::new).contains(item[1])));
      }

      //assert completion stats
      assertions.add(() -> Assertions.assertEquals("Total: " + providedAndPredictedTokens.length + " nglm",
          actual.stream().filter(l -> l.contains("Total")).findFirst().orElseThrow(AssertionError::new)));

      Assertions.assertAll(assertions);
    }
  }

  private static Stream<Arguments> provideNgramDictionaryXML() {

    List<Arguments> arguments = new LinkedList<>();

    List<Map<String, String[][]>> testFileNamesWithProvidedAndPredictedTokens =
        List.of(Map.of("sentences_set_1",
                new String[][] {{"data availability is by", "now"},
                    {"machine and deep learning", "algorithms"}}),
            Map.of("sentences_set_2", new String[][] {{"lunar landing mission was the", "first"}}));

    for (Map<String, String[][]> testInput : testFileNamesWithProvidedAndPredictedTokens) {

      NGramModel ngModel = new NGramModel();

      for (Map.Entry<String, String[][]> entry : testInput.entrySet()) {

        try (InputStream is = ngModel.getClass().getResourceAsStream(
            String.format("/opennlp/tools/cmdline/languagemodel/%s.txt", entry.getKey()));
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             FileOutputStream fos = new FileOutputStream(new File(testDir,
                 String.format("%s_ngram_dict.xml", entry.getKey())))) {

          //Read the test data file line by line and generate ngrams based on MIN_LENGTH and MAX_LENGTH.
          reader.lines()
              .map(l -> NGramGenerator.generate(Arrays.asList(l.split(" ")), NGRAM_MAX_LENGTH, " "))
              .flatMap(Collection::stream)
              .forEach(t -> ngModel.add(new StringList(t.split(" ")), NGRAM_MIN_LENGTH, NGRAM_MAX_LENGTH));

          //Output the ngram dictionary in a test file.
          ngModel.serialize(fos);

          //create input arguments for the test method.
          arguments.add(
              Arguments.of(String.format("%s_ngram_dict.xml", entry.getKey()),
                  entry.getValue()));

        } catch (IOException e) {
          throw new TerminateToolException(-1,
              "IO Error while creating test data files " + e.getMessage(), e);
        }
      }
    }
    return arguments.stream();
  }

  /**
   * Restores testing resources to original configuration.
   */
  @AfterAll
  public static void afterAll() {
    restore(LOGGER_OPENNLP);
    System.setIn(sysInputStream);
  }
}
