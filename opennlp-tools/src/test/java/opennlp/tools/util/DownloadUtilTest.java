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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import opennlp.tools.EnabledWhenCDNAvailable;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerModel;

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
  @ValueSource(strings = {"en", "fr", "de", "it", "nl"})
  @EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
  public void testDownloadModelByLanguage(String lang) throws IOException {
    SentenceModel model = DownloadUtil.downloadModel(lang,
            DownloadUtil.ModelType.SENTENCE_DETECTOR, SentenceModel.class);
    Assertions.assertNotNull(model);
    Assertions.assertEquals(lang, model.getLanguage());
    Assertions.assertTrue(model.isLoadedFromSerialized());
  }

  @ParameterizedTest(name = "Verify \"{0}\" tokenizer model")
  @MethodSource(value = "provideURLs")
  @EnabledWhenCDNAvailable(hostname = "dlcdn.apache.org")
  public void testDownloadModelByURL(String language, URL url) throws IOException {
    TokenizerModel model = DownloadUtil.downloadModel(url, TokenizerModel.class);
    Assertions.assertNotNull(model);
    Assertions.assertEquals(language, model.getLanguage());
    Assertions.assertTrue(model.isLoadedFromSerialized());
  }

  @ParameterizedTest(name = "Detect invalid input: \"{0}\"")
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n"})
  public void testDownloadModelInvalid(String input) {
    Assertions.assertThrows(IOException.class, () -> DownloadUtil.downloadModel(
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
            Arguments.of("nl", DownloadUtil.available_models.get("nl").get(MT_TOKENIZER))
    );
  }
}
