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

import opennlp.tools.models.ModelType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DownloadParserTest {

  @ParameterizedTest(name = "Verify \"{0}\" available models")
  @MethodSource(value = "expectedModels")
  void testAvailableModels(String language, Map<ModelType, String> expectedModels) {

    final URL baseUrl = fromClasspath("opennlp/tools/util/index.html");
    assertNotNull(baseUrl);

    final DownloadUtil.DownloadParser downloadParser = new DownloadUtil.DownloadParser(baseUrl);

    Map<String, Map<ModelType, String>> result = downloadParser.getAvailableModels();

    assertNotNull(result);
    assertEquals(32, result.size());

    final Map<ModelType, String> availableModels = result.get(language);
    assertNotNull(availableModels);

    for (Map.Entry<ModelType, String> e : expectedModels.entrySet()) {
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
    Map<String, Map<ModelType, String>> result = downloadParser.getAvailableModels();
    assertNotNull(result);
    assertEquals(0, result.size());
  }

  private URL fromClasspath(String file) {
    return Thread.currentThread().getContextClassLoader().getResource(file);
  }

  private static final String OPENNLP = "opennlp-";
  private static final String MODEL_SENT = "sentence-";
  private static final String MODEL_TOK = "tokens-";
  private static final String MODEL_POS = "pos-";
  private static final String VER = "1.2-2.5.0";
  private static final String BIN = ".bin";

  // Note: This needs to be public as JUnit 5 requires it like this.
  public static Stream<Arguments> expectedModels() {
    // Data as defined in "test/resources/opennlp/tools/util/index.html"
    return Stream.of(
      Arguments.of("en",Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "en-ud-ewt-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "en-ud-ewt-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "en-ud-ewt-" + MODEL_POS + VER + BIN)),
      Arguments.of("fr", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "fr-ud-gsd-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "fr-ud-gsd-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "fr-ud-gsd-" + MODEL_POS + VER + BIN)),
      Arguments.of("de", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "de-ud-gsd-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "de-ud-gsd-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "de-ud-gsd-" + MODEL_POS + VER + BIN)),
      Arguments.of("it", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "it-ud-vit-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "it-ud-vit-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "it-ud-vit-" + MODEL_POS + VER + BIN)),
      Arguments.of("nl", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "nl-ud-alpino-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "nl-ud-alpino-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "nl-ud-alpino-" + MODEL_POS + VER + BIN)),
      Arguments.of("bg", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "bg-ud-btb-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "bg-ud-btb-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "bg-ud-btb-" + MODEL_POS + VER + BIN)),
      Arguments.of("cs", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "cs-ud-pdt-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "cs-ud-pdt-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "cs-ud-pdt-" + MODEL_POS + VER + BIN)),
      Arguments.of("da", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "da-ud-ddt-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "da-ud-ddt-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "da-ud-ddt-" + MODEL_POS + VER + BIN)),
      Arguments.of("es", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "es-ud-gsd-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "es-ud-gsd-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "es-ud-gsd-" + MODEL_POS + VER + BIN)),
      Arguments.of("et", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "et-ud-edt-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "et-ud-edt-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "et-ud-edt-" + MODEL_POS + VER + BIN)),
      Arguments.of("fi", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "fi-ud-tdt-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "fi-ud-tdt-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "fi-ud-tdt-" + MODEL_POS + VER + BIN)),
      Arguments.of("hr", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "hr-ud-set-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "hr-ud-set-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "hr-ud-set-" + MODEL_POS + VER + BIN)),
      Arguments.of("lv", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "lv-ud-lvtb-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "lv-ud-lvtb-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "lv-ud-lvtb-" + MODEL_POS + VER + BIN)),
      Arguments.of("no", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "no-ud-bokmaal-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "no-ud-bokmaal-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "no-ud-bokmaal-" + MODEL_POS + VER + BIN)),
      Arguments.of("pl", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "pl-ud-pdb-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "pl-ud-pdb-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "pl-ud-pdb-" + MODEL_POS + VER + BIN)),
      Arguments.of("pt", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "pt-ud-gsd-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "pt-ud-gsd-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "pt-ud-gsd-" + MODEL_POS + VER + BIN)),
      Arguments.of("ro", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "ro-ud-rrt-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "ro-ud-rrt-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "ro-ud-rrt-" + MODEL_POS + VER + BIN)),
      Arguments.of("ru", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "ru-ud-gsd-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "ru-ud-gsd-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "ru-ud-gsd-" + MODEL_POS + VER + BIN)),
      Arguments.of("sr", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "sr-ud-set-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "sr-ud-set-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "sr-ud-set-" + MODEL_POS + VER + BIN)),
      Arguments.of("sk", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "sk-ud-snk-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "sk-ud-snk-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "sk-ud-snk-" + MODEL_POS + VER + BIN)),
      Arguments.of("sl", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "sl-ud-ssj-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "sl-ud-ssj-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "sl-ud-ssj-" + MODEL_POS + VER + BIN)),
      Arguments.of("sv", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "sv-ud-talbanken-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "sv-ud-talbanken-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "sv-ud-talbanken-" + MODEL_POS + VER + BIN)),
      Arguments.of("uk", Map.of(
        ModelType.SENTENCE_DETECTOR, OPENNLP + "uk-ud-iu-" + MODEL_SENT + VER + BIN,
        ModelType.TOKENIZER, OPENNLP + "uk-ud-iu-" + MODEL_TOK + VER + BIN,
        ModelType.POS, OPENNLP + "uk-ud-iu-" + MODEL_POS + VER + BIN))
    );
  }
}
