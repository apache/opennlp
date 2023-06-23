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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DownloadParserTest {

  @ParameterizedTest(name = "Verify \"{0}\" available models")
  @MethodSource(value = "expectedModels")
  void testAvailableModels(String language, Map<DownloadUtil.ModelType, String> expectedModels) {

    final URL baseUrl = fromClasspath("opennlp/tools/util/index.html");
    assertNotNull(baseUrl);

    final DownloadUtil.DownloadParser downloadParser = new DownloadUtil.DownloadParser(baseUrl);

    Map<String, Map<DownloadUtil.ModelType, String>> result = downloadParser.getAvailableModels();

    assertNotNull(result);
    assertEquals(5, result.size());

    final Map<DownloadUtil.ModelType, String> availableModels = result.get(language);
    assertNotNull(availableModels);

    for (Map.Entry<DownloadUtil.ModelType, String> e : expectedModels.entrySet()) {
      final String url = availableModels.get(e.getKey());
      final String expectedUrl = baseUrl + "/" + e.getValue();

      assertNotNull(url, "A model for the given model type is expected");
      assertEquals(expectedUrl, url);
    }
  }

  @Test
  void testNullUrl() {
    assertThrows(NullPointerException.class, () -> new DownloadUtil.DownloadParser(null)
    );
  }

  @Test
  void testInvalidUrl() throws MalformedURLException {
    final DownloadUtil.DownloadParser downloadParser =
        new DownloadUtil.DownloadParser(new URL("file:/this/does/not/exist"));
    Map<String, Map<DownloadUtil.ModelType, String>> result = downloadParser.getAvailableModels();
    assertNotNull(result);
    assertEquals(0, result.size());
  }

  private URL fromClasspath(String file) {
    return Thread.currentThread().getContextClassLoader().getResource(file);
  }

  // Note: This needs to be public as JUnit 5 requires it like this.
  public static Stream<Arguments> expectedModels() {
    // Data as defined in "test/resources/opennlp/tools/util/index.html"
    return Stream.of(
        Arguments.of("en",
            Map.of(
                DownloadUtil.ModelType.SENTENCE_DETECTOR, "opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin",
                DownloadUtil.ModelType.TOKENIZER, "opennlp-en-ud-ewt-tokens-1.0-1.9.3.bin",
                DownloadUtil.ModelType.POS, "opennlp-en-ud-ewt-pos-1.0-1.9.3.bin")),
        Arguments.of("fr",
            Map.of(
                DownloadUtil.ModelType.SENTENCE_DETECTOR, "opennlp-1.0-1.9.3fr-ud-ftb-sentence-1.0-1.9.3.bin",
                DownloadUtil.ModelType.TOKENIZER, "opennlp-fr-ud-ftb-tokens-1.0-1.9.3.bin",
                DownloadUtil.ModelType.POS, "opennlp-fr-ud-ftb-pos-1.0-1.9.3.bin")),
        Arguments.of("de",
            Map.of(
                DownloadUtil.ModelType.SENTENCE_DETECTOR, "opennlp-de-ud-gsd-sentence-1.0-1.9.3.bin",
                DownloadUtil.ModelType.TOKENIZER, "opennlp-de-ud-gsd-tokens-1.0-1.9.3.bin",
                DownloadUtil.ModelType.POS, "opennlp-de-ud-gsd-pos-1.0-1.9.3.bin")),
        Arguments.of("it",
            Map.of(
                DownloadUtil.ModelType.SENTENCE_DETECTOR, "opennlp-it-ud-vit-sentence-1.0-1.9.3.bin",
                DownloadUtil.ModelType.TOKENIZER, "opennlp-it-ud-vit-tokens-1.0-1.9.3.bin",
                DownloadUtil.ModelType.POS, "opennlp-it-ud-vit-pos-1.0-1.9.3.bin")),
        Arguments.of("nl",
            Map.of(
                DownloadUtil.ModelType.SENTENCE_DETECTOR, "opennlp-nl-ud-alpino-sentence-1.0-1.9.3.bin",
                DownloadUtil.ModelType.TOKENIZER, "opennlp-nl-ud-alpino-tokens-1.0-1.9.3.bin",
                DownloadUtil.ModelType.POS, "opennlp-nl-ud-alpino-pos-1.0-1.9.3.bin"))
    );
  }
}
