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

package opennlp.tools.stemmer.hunspell;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.util.DictionaryCatalog;
import opennlp.tools.util.DownloadUtil;

/**
 * Pins the Hunspell catalog download gate; network fetches are not exercised here.
 */
public class HunspellDictionaryDownloadTest {

  @Test
  void testDownloadRequiresRemoteProperty(@TempDir Path target) {
    final String previous = System.getProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY);
    System.clearProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY);
    try {
      final IOException e = Assertions.assertThrows(IOException.class,
          () -> HunspellDictionaryDownload.downloadFromCatalog("en_US", target));
      Assertions.assertTrue(e.getMessage().contains(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY));
    } finally {
      if (previous == null) {
        System.clearProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY);
      } else {
        System.setProperty(DownloadUtil.REMOTE_DOWNLOAD_PROPERTY, previous);
      }
    }
  }

  @Test
  void testCatalogContainsEnUsPair() throws IOException {
    final DictionaryCatalog catalog = DictionaryCatalog.loadDefault();
    Assertions.assertTrue(catalog.ids().contains("hunspell.en_US.aff"));
    Assertions.assertTrue(catalog.ids().contains("hunspell.en_US.dic"));
    Assertions.assertTrue(catalog.ids().contains("hunspell.en_US.readme"));
    Assertions.assertEquals(128, catalog.get("hunspell.en_US.aff").sha512().length());
  }
}
