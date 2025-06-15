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

import java.io.IOException;
import java.net.URL;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.EnabledWhenCDNAvailable;
import opennlp.tools.models.ModelType;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DownloadUtilTest {

  @ParameterizedTest(name = "Verify \"{0}\" sentence model")
  @ValueSource(strings = {"en", "fr", "de", "it", "nl", "bg", "ca", "cs", "da", "el",
      "es", "et", "eu", "fi", "hr", "hy", "is", "ka", "kk", "ko",
      "lv", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sr", "sv",
      "tr", "uk"})
  @EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
  public void testDownloadModelByLanguage(String lang) throws IOException {
    SentenceModel model = DownloadUtil.downloadModel(lang,
        ModelType.SENTENCE_DETECTOR, SentenceModel.class);
    assertNotNull(model);
    assertEquals(lang, model.getLanguage());
    assertTrue(model.isLoadedFromSerialized());
  }

  @ParameterizedTest(name = "Verify \"{0}\" tokenizer model")
  @MethodSource(value = "provideURLs")
  @EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
  public void testDownloadModelByURL(String language, URL url) throws IOException {
    TokenizerModel model = DownloadUtil.downloadModel(url, TokenizerModel.class);
    assertNotNull(model);
    assertEquals(language, model.getLanguage());
    assertTrue(model.isLoadedFromSerialized());
  }

  @Test
  @EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
  public void testExistsModel() throws IOException {
    final String lang = "en";
    final ModelType type = ModelType.SENTENCE_DETECTOR;
    // Prepare
    SentenceModel model = DownloadUtil.downloadModel(lang, type, SentenceModel.class);
    assertNotNull(model);
    assertEquals(lang, model.getLanguage());
    // Test
    assertTrue(DownloadUtil.existsModel(lang, type));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"xy", "\t", "\n"})
  @EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
  public void testExistsModelInvalid(String input) throws IOException {
    assertFalse(DownloadUtil.existsModel(input, ModelType.SENTENCE_DETECTOR));
  }

  @ParameterizedTest(name = "Detect invalid input: \"{0}\"")
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n"})
  public void testDownloadModelInvalid(String input) {
    assertThrows(IOException.class, () -> DownloadUtil.downloadModel(input,
            ModelType.SENTENCE_DETECTOR, SentenceModel.class), "Invalid model");
  }

  private static final ModelType MT_TOKENIZER = ModelType.TOKENIZER;

  // Note: This needs to be public as JUnit 5 requires it like this.
  public static Stream<Arguments> provideURLs() {
    return Stream.of(
        Arguments.of("en", DownloadUtil.getAvailableModels().get("en").get(MT_TOKENIZER)),
        Arguments.of("fr", DownloadUtil.getAvailableModels().get("fr").get(MT_TOKENIZER)),
        Arguments.of("de", DownloadUtil.getAvailableModels().get("de").get(MT_TOKENIZER)),
        Arguments.of("it", DownloadUtil.getAvailableModels().get("it").get(MT_TOKENIZER)),
        Arguments.of("nl", DownloadUtil.getAvailableModels().get("nl").get(MT_TOKENIZER)),
        Arguments.of("bg", DownloadUtil.getAvailableModels().get("bg").get(MT_TOKENIZER)),
        Arguments.of("ca", DownloadUtil.getAvailableModels().get("ca").get(MT_TOKENIZER)),
        Arguments.of("cs", DownloadUtil.getAvailableModels().get("cs").get(MT_TOKENIZER)),
        Arguments.of("da", DownloadUtil.getAvailableModels().get("da").get(MT_TOKENIZER)),
        Arguments.of("el", DownloadUtil.getAvailableModels().get("el").get(MT_TOKENIZER)),
        Arguments.of("es", DownloadUtil.getAvailableModels().get("es").get(MT_TOKENIZER)),
        Arguments.of("et", DownloadUtil.getAvailableModels().get("et").get(MT_TOKENIZER)),
        Arguments.of("eu", DownloadUtil.getAvailableModels().get("eu").get(MT_TOKENIZER)),
        Arguments.of("fi", DownloadUtil.getAvailableModels().get("fi").get(MT_TOKENIZER)),
        Arguments.of("hr", DownloadUtil.getAvailableModels().get("hr").get(MT_TOKENIZER)),
        Arguments.of("hy", DownloadUtil.getAvailableModels().get("hy").get(MT_TOKENIZER)),
        Arguments.of("is", DownloadUtil.getAvailableModels().get("is").get(MT_TOKENIZER)),
        Arguments.of("ka", DownloadUtil.getAvailableModels().get("ka").get(MT_TOKENIZER)),
        Arguments.of("kk", DownloadUtil.getAvailableModels().get("kk").get(MT_TOKENIZER)),
        Arguments.of("ko", DownloadUtil.getAvailableModels().get("ko").get(MT_TOKENIZER)),
        Arguments.of("lv", DownloadUtil.getAvailableModels().get("lv").get(MT_TOKENIZER)),
        Arguments.of("no", DownloadUtil.getAvailableModels().get("no").get(MT_TOKENIZER)),
        Arguments.of("pl", DownloadUtil.getAvailableModels().get("pl").get(MT_TOKENIZER)),
        Arguments.of("pt", DownloadUtil.getAvailableModels().get("pt").get(MT_TOKENIZER)),
        Arguments.of("ro", DownloadUtil.getAvailableModels().get("ro").get(MT_TOKENIZER)),
        Arguments.of("ru", DownloadUtil.getAvailableModels().get("ru").get(MT_TOKENIZER)),
        Arguments.of("sk", DownloadUtil.getAvailableModels().get("sk").get(MT_TOKENIZER)),
        Arguments.of("sl", DownloadUtil.getAvailableModels().get("sl").get(MT_TOKENIZER)),
        Arguments.of("sr", DownloadUtil.getAvailableModels().get("sr").get(MT_TOKENIZER)),
        Arguments.of("sv", DownloadUtil.getAvailableModels().get("sv").get(MT_TOKENIZER)),
        Arguments.of("tr", DownloadUtil.getAvailableModels().get("tr").get(MT_TOKENIZER)),
        Arguments.of("uk", DownloadUtil.getAvailableModels().get("uk").get(MT_TOKENIZER))
    );
  }
}
