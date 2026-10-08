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

package opennlp.tools.tokenize.lattice;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import opennlp.tools.util.DictionaryCatalog;
import opennlp.tools.util.ParamChecks;
import opennlp.tools.util.ResourceInstaller;

/**
 * Fetches and unpacks a MeCab-format dictionary archive into a local directory, so the
 * dictionary is acquired by the user at install time and never ships with this library.
 * No dictionary data is bundled. Fetching, verification, and unpacking are done by
 * {@link ResourceInstaller} under {@link ResourceInstaller.Limits#DEFAULT}, including
 * startup property overrides. An {@code http} or {@code https} archive requires an
 * expected checksum. Gzip-compressed tar archives in the ustar, pax, and GNU formats
 * and zip archives are read. Catalog installs are opt-in via
 * {@link #installFromCatalog(DictionaryCatalog, String, Path)}.
 *
 * <p>Only the dictionary payload is installed: the {@code *.csv} lexicon files and
 * {@code *.def} definition files that a {@link MecabDictionary} reads, plus the
 * {@code dicrc} configuration file distributions ship alongside them, taken from the
 * archive root only (at most one leading directory deep). Deeper entries are skipped:
 * mecab-ko-dic, for example, nests {@code user-dic} templates whose numeric fields are
 * empty because they are input for {@code mecab-dict-index}, not loadable lexicon
 * data. Installed files are flattened to their base names. A file whose base name
 * already exists in the target is not replaced, so it must be removed before refreshing
 * a dictionary. The archive unpacks into a hidden staging directory beneath the target,
 * on the target's filesystem, which is removed when the installation ends.</p>
 *
 * @since 3.0.0
 */
public final class MecabDictionaryInstaller {

  /** The deepest entry path, relative to the archive root, that holds payload. */
  private static final int MAX_PAYLOAD_DEPTH = 2;

  /** Prevents construction of this utility class. */
  private MecabDictionaryInstaller() {
  }

  /**
   * Unpacks a local {@code file:} archive URI. Any other scheme requires
   * {@link #install(URI, Path, String)} with an expected checksum.
   *
   * @param archive The archive location, a gzip-compressed tar or a zip archive. Must
   *                not be {@code null}.
   * @param targetDirectory The directory to unpack into; created when absent. Must not
   *                        be {@code null}.
   * @return The number of dictionary files installed.
   * @throws IOException Thrown if reading or writing fails, the archive contains no
   *         dictionary file, an installation limit is exceeded, or the target already
   *         contains one of the files.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null} or
   *         {@code archive} does not use the {@code file} scheme.
   */
  public static int install(URI archive, Path targetDirectory) throws IOException {
    return install(archive, targetDirectory, null);
  }

  /**
   * Downloads a dictionary archive when needed, verifies its checksum, and unpacks it
   * through {@link ResourceInstaller#install(URI, Path, String)}. A {@code file:} URI
   * may omit the checksum.
   *
   * @param archive The archive location, a gzip-compressed tar or a zip archive. Must
   *                not be {@code null}.
   * @param targetDirectory The directory to unpack into; created when absent. Must not
   *                        be {@code null}.
   * @param expectedChecksum The expected digest of the archive bytes as a hex string,
   *                         64 characters for SHA-256 or 128 for SHA-512. Required for
   *                         an http or https source; pass {@code null} to skip
   *                         verification for a file source.
   * @return The number of dictionary files installed.
   * @throws IOException Thrown if fetching, verification, reading, or writing fails,
   *         the archive contains no dictionary file, an installation limit is
   *         exceeded, or the target already contains one of the files.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}, the URI is
   *         not supported by {@link ResourceInstaller}, or an http or https source
   *         has no checksum.
   */
  public static int install(URI archive, Path targetDirectory, String expectedChecksum)
      throws IOException {
    ParamChecks.requireNonNullArg(archive, "archive");
    ParamChecks.requireNonNullArg(targetDirectory, "targetDirectory");
    return installDictionaryFiles(targetDirectory,
        staging -> ResourceInstaller.install(archive, staging, expectedChecksum));
  }

  /**
   * Downloads a dictionary named in an application-supplied
   * {@link DictionaryCatalog} and unpacks it. Requires
   * {@link DictionaryCatalog#REMOTE_DOWNLOAD_PROPERTY} to be {@code true}.
   *
   * @param catalog The application-supplied catalog. Must not be {@code null}.
   * @param dictionaryId The catalog id, for example {@code mecab.ipadic} or
   *                     {@code mecab.ko-dic}. Must not be {@code null}.
   * @param targetDirectory The directory to unpack into; created when absent. Must not
   *                        be {@code null}.
   * @return The number of dictionary files installed.
   * @throws IOException Thrown if the catalog entry is missing, remote downloads are
   *         disabled, or install fails.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public static int installFromCatalog(DictionaryCatalog catalog, String dictionaryId,
      Path targetDirectory) throws IOException {
    ParamChecks.requireNonNullArg(catalog, "catalog");
    ParamChecks.requireNonNullArg(dictionaryId, "dictionaryId");
    ParamChecks.requireNonNullArg(targetDirectory, "targetDirectory");
    return installDictionaryFiles(targetDirectory,
        staging -> catalog.install(dictionaryId, staging));
  }

  /**
   * Unpacks an archive through the given step and installs its dictionary payload into
   * the target, flattened to base names.
   *
   * @param targetDirectory The directory to install into; created when absent.
   * @param unpack Unpacks the archive into the staging directory it is given.
   * @return The number of dictionary files installed.
   * @throws IOException Thrown if unpacking fails, the archive holds no dictionary file,
   *         two entries flatten to the same base name, a target file already exists, or
   *         moving fails.
   */
  private static int installDictionaryFiles(Path targetDirectory,
      ResourceInstaller.StagingStep unpack) throws IOException {
    final int installed = ResourceInstaller.installSelected(targetDirectory, unpack,
        MecabDictionaryInstaller::payloadName);
    if (installed == 0) {
      throw new IOException("the archive contains no dictionary file");
    }
    return installed;
  }

  /**
   * Selects the dictionary payload of an unpacked archive and flattens it.
   *
   * @param relative An unpacked file's path relative to the archive root.
   * @return The file's base name when it is dictionary payload at most
   *         {@value #MAX_PAYLOAD_DEPTH} levels deep, or {@code null} otherwise.
   */
  private static Path payloadName(Path relative) {
    final Path baseName = relative.getFileName();
    return relative.getNameCount() <= MAX_PAYLOAD_DEPTH
        && isDictionaryFile(baseName.toString()) ? baseName : null;
  }

  /**
   * Recognizes the file names a {@link MecabDictionary} loads.
   *
   * @param baseName The file name without any directory prefix.
   * @return {@code true} when the name is dictionary payload.
   */
  private static boolean isDictionaryFile(String baseName) {
    return baseName.endsWith(MecabDictionary.LEXICON_EXTENSION)
        || baseName.endsWith(MecabDictionary.DEFINITION_EXTENSION)
        || MecabDictionary.CONFIGURATION_FILE.equals(baseName);
  }
}
