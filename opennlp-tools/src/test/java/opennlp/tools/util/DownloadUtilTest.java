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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.EnabledWhenCDNAvailable;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DownloadUtilTest {

  private static final String APACHE_CDN = "dlcdn.apache.org";

  @BeforeAll
  public static void cleanupWhenOnline() {
    boolean isOnline;
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(APACHE_CDN, 80), EnabledWhenCDNAvailable.TIMEOUT_MS);
      isOnline = true;
    } catch (IOException e) {
      // Unreachable, unresolvable or timeout
      isOnline = false;
    }
    // If CDN is available -> go cleanup in preparation of the actual tests
    if (isOnline) {
      wipeExistingModelFiles("-tokens-");
      wipeExistingModelFiles("-sentence-");
    }
  }

  /*
   * Helper method that wipes out mode files if they exist on the text execution env.
   * Those model files are wiped from the user's home hidden '.opennlp' subdirectory.
   *
   * Thereby, a clean download can be guaranteed - ín CDN is available and test are executed.
   */
  private static void wipeExistingModelFiles(final String fragment) {
    final String openNLPHomeDir = System.getProperty("user.home") + "/.opennlp/";
    final Path dir = FileSystems.getDefault().getPath(openNLPHomeDir);
    if (Files.exists(dir)) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*opennlp-*" + fragment + "*")) {
        for (Path modelFileToWipe: stream) {
          Files.deleteIfExists(modelFileToWipe);
        }
      } catch (IOException e) {
        fail(e.getLocalizedMessage());
      }
    }
  }

  @ParameterizedTest(name = "Verify \"{0}\" sentence model")
  @ValueSource(strings = {"en", "fr", "de", "it", "nl", "bg", "ca", "cs", "da", "el",
                          "es", "et", "eu", "fi", "hr", "hy", "is", "ka", "kk", "ko",
                          "lv", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sr", "sv",
                          "tr", "uk"})
  @EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
  public void testDownloadModelByLanguage(String lang) throws IOException {
    SentenceModel model = DownloadUtil.downloadModel(lang,
            DownloadUtil.ModelType.SENTENCE_DETECTOR, SentenceModel.class);
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

  @ParameterizedTest(name = "Detect invalid input: \"{0}\"")
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n"})
  public void testDownloadModelInvalid(String input) {
    assertThrows(IOException.class, () -> DownloadUtil.downloadModel(
                    input, DownloadUtil.ModelType.SENTENCE_DETECTOR, SentenceModel.class),
            "Invalid model");
  }

  private static final DownloadUtil.ModelType MT_TOKENIZER = DownloadUtil.ModelType.TOKENIZER;

  // Note: This needs to be public as JUnit 5 requires it like this.
  public static Stream<Arguments> provideURLs() {
    return Stream.of(
            Arguments.of("en", DownloadUtil.available_models.get("en").get(MT_TOKENIZER)),
            Arguments.of("fr", DownloadUtil.available_models.get("fr").get(MT_TOKENIZER)),
            Arguments.of("de", DownloadUtil.available_models.get("de").get(MT_TOKENIZER)),
            Arguments.of("it", DownloadUtil.available_models.get("it").get(MT_TOKENIZER)),
            Arguments.of("nl", DownloadUtil.available_models.get("nl").get(MT_TOKENIZER)),
            Arguments.of("bg", DownloadUtil.available_models.get("bg").get(MT_TOKENIZER)),
            Arguments.of("ca", DownloadUtil.available_models.get("ca").get(MT_TOKENIZER)),
            Arguments.of("cs", DownloadUtil.available_models.get("cs").get(MT_TOKENIZER)),
            Arguments.of("da", DownloadUtil.available_models.get("da").get(MT_TOKENIZER)),
            Arguments.of("el", DownloadUtil.available_models.get("el").get(MT_TOKENIZER)),
            Arguments.of("es", DownloadUtil.available_models.get("es").get(MT_TOKENIZER)),
            Arguments.of("et", DownloadUtil.available_models.get("et").get(MT_TOKENIZER)),
            Arguments.of("eu", DownloadUtil.available_models.get("eu").get(MT_TOKENIZER)),
            Arguments.of("fi", DownloadUtil.available_models.get("fi").get(MT_TOKENIZER)),
            Arguments.of("hr", DownloadUtil.available_models.get("hr").get(MT_TOKENIZER)),
            Arguments.of("hy", DownloadUtil.available_models.get("hy").get(MT_TOKENIZER)),
            Arguments.of("is", DownloadUtil.available_models.get("is").get(MT_TOKENIZER)),
            Arguments.of("ka", DownloadUtil.available_models.get("ka").get(MT_TOKENIZER)),
            Arguments.of("kk", DownloadUtil.available_models.get("kk").get(MT_TOKENIZER)),
            Arguments.of("ko", DownloadUtil.available_models.get("ko").get(MT_TOKENIZER)),
            Arguments.of("lv", DownloadUtil.available_models.get("lv").get(MT_TOKENIZER)),
            Arguments.of("no", DownloadUtil.available_models.get("no").get(MT_TOKENIZER)),
            Arguments.of("pl", DownloadUtil.available_models.get("pl").get(MT_TOKENIZER)),
            Arguments.of("pt", DownloadUtil.available_models.get("pt").get(MT_TOKENIZER)),
            Arguments.of("ro", DownloadUtil.available_models.get("ro").get(MT_TOKENIZER)),
            Arguments.of("ru", DownloadUtil.available_models.get("ru").get(MT_TOKENIZER)),
            Arguments.of("sk", DownloadUtil.available_models.get("sk").get(MT_TOKENIZER)),
            Arguments.of("sl", DownloadUtil.available_models.get("sl").get(MT_TOKENIZER)),
            Arguments.of("sr", DownloadUtil.available_models.get("sr").get(MT_TOKENIZER)),
            Arguments.of("sv", DownloadUtil.available_models.get("sv").get(MT_TOKENIZER)),
            Arguments.of("tr", DownloadUtil.available_models.get("tr").get(MT_TOKENIZER)),
            Arguments.of("uk", DownloadUtil.available_models.get("uk").get(MT_TOKENIZER))
    );
  }
}
