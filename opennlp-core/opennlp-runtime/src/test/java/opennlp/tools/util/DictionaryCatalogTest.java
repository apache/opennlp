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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the opt-in dictionary catalog against an in-memory properties file and a
 * local file URI so no network access is required.
 */
public class DictionaryCatalogTest {

  @Test
  void testDownloadRequiresRemoteProperty(@TempDir Path dir) throws Exception {
    final byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
    final DictionaryCatalog loaded = demoCatalog(dir, payload);

    final String previous = System.getProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY);
    System.clearProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY);
    try {
      final IOException e = Assertions.assertThrows(IOException.class,
          () -> loaded.download("demo", dir.resolve("out.bin")));
      Assertions.assertTrue(e.getMessage().contains(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY));
    } finally {
      restore(previous);
    }
  }

  @Test
  void testDownloadWithRemotePropertyEnabled(@TempDir Path dir) throws Exception {
    final byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
    final DictionaryCatalog loaded = demoCatalog(dir, payload);
    final Path target = dir.resolve("out.bin");

    final String previous = System.getProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY);
    System.setProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY, "true");
    try {
      loaded.download("demo", target);
      Assertions.assertArrayEquals(payload, Files.readAllBytes(target));
    } finally {
      restore(previous);
    }
  }

  @Test
  void testDefaultCatalogContainsMecabAndHunspellEntries() throws IOException {
    final DictionaryCatalog catalog = DictionaryCatalog.loadDefault();
    Assertions.assertTrue(catalog.ids().contains("mecab.ipadic"));
    Assertions.assertTrue(catalog.ids().contains("mecab.ko-dic"));
    Assertions.assertTrue(catalog.ids().contains("hunspell.en_US.aff"));
    Assertions.assertEquals(128, catalog.get("mecab.ipadic").sha512().length());
    Assertions.assertEquals(128, catalog.get("hunspell.en_US.dic").sha512().length());
  }

  private static DictionaryCatalog demoCatalog(Path dir, byte[] payload)
      throws IOException {
    final Path source = dir.resolve("dict.bin");
    Files.write(source, payload);
    final String catalog = "demo.url=" + source.toUri() + "\n"
        + "demo.sha512=" + DigestTestUtil.sha512(payload) + "\n";
    return DictionaryCatalog.load(
        new ByteArrayInputStream(catalog.getBytes(StandardCharsets.UTF_8)));
  }

  private static void restore(String previous) {
    if (previous == null) {
      System.clearProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY);
    } else {
      System.setProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY, previous);
    }
  }
}
