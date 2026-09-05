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

import opennlp.tools.util.DictionaryCatalog;

/**
 * Opt-in download of Hunspell {@code .aff}/{@code .dic} pairs and their license
 * readme from an application-supplied {@link DictionaryCatalog}. Requires
 * {@code -Dopennlp.download.remote=true}. OpenNLP bundles neither a catalog nor
 * dictionary data.
 *
 * @since 3.0.0
 */
public final class HunspellDictionaryDownload {

  /** Prevents construction of this utility class. */
  private HunspellDictionaryDownload() {
  }

  /**
   * Downloads the cataloged {@code .aff}, {@code .dic}, and readme files for
   * {@code dictionaryId} into {@code targetDirectory}. Each file uses its configured
   * name or source name, for example {@code en_US.aff}. Existing target files are not
   * replaced, so they must be removed before refreshing a dictionary.
   *
   * @param catalog The application-supplied catalog. Must not be {@code null}.
   * @param dictionaryId The catalog dictionary name, for example {@code en_US}.
   *                     Must not be {@code null}.
   * @param targetDirectory The directory to write into; created when absent. Must not
   *                        be {@code null}.
   * @throws IOException Thrown if remote downloads are disabled, a catalog entry is
   *         missing, verification fails, or the target already contains one of the
   *         files.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static void downloadFromCatalog(DictionaryCatalog catalog, String dictionaryId,
      Path targetDirectory) throws IOException {
    if (catalog == null) {
      throw new IllegalArgumentException("catalog must not be null");
    }
    if (dictionaryId == null) {
      throw new IllegalArgumentException("dictionaryId must not be null");
    }
    if (targetDirectory == null) {
      throw new IllegalArgumentException("targetDirectory must not be null");
    }
    final String prefix = "hunspell." + dictionaryId;
    catalog.install(prefix + HunspellDictionary.AFFIX_FILE_SUFFIX, targetDirectory);
    catalog.install(prefix + HunspellDictionary.DICTIONARY_FILE_SUFFIX, targetDirectory);
    final String readmeId = prefix + ".readme";
    if (catalog.ids().contains(readmeId)) {
      catalog.install(readmeId, targetDirectory);
    }
  }
}
