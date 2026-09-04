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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Opt-in catalog of remote dictionary archives and companion files, loaded from
 * application-supplied properties containing URLs and SHA-512 digests. Fetching an
 * entry requires {@link #REMOTE_DOWNLOAD_PROPERTY} to be {@code true}.
 *
 * @since 3.0.0
 */
public final class DictionaryCatalog {

  private static final int SHA_512_HEX_LENGTH = 128;
  private static final String URL_SUFFIX = ".url";

  /**
   * System property that must be {@code true} before a catalog entry may be
   * fetched. Direct {@link ResourceInstaller} calls do not require it: there the
   * caller already supplied the URI and digest.
   */
  public static final String REMOTE_DOWNLOAD_PROPERTY = "opennlp.download.remote";
  private final Properties properties;

  /**
   * Initializes a catalog from loaded properties.
   *
   * @param properties The catalog properties.
   */
  private DictionaryCatalog(Properties properties) {
    this.properties = properties;
  }

  /**
   * Loads a catalog from a properties stream.
   *
   * @param in The properties content. Must not be {@code null}.
   * @return The catalog. Never {@code null}.
   * @throws IOException Thrown if reading fails.
   * @throws IllegalArgumentException Thrown if {@code in} is {@code null}.
   */
  public static DictionaryCatalog load(InputStream in) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("in must not be null");
    }
    final Properties properties = new Properties();
    properties.load(in);
    return new DictionaryCatalog(properties);
  }

  /**
   * {@return an unmodifiable set of catalog entry ids}
   */
  public Set<String> ids() {
    final Set<String> ids = new LinkedHashSet<>();
    for (final String key : properties.stringPropertyNames()) {
      if (key.endsWith(URL_SUFFIX)) {
        ids.add(key.substring(0, key.length() - URL_SUFFIX.length()));
      }
    }
    return Collections.unmodifiableSet(ids);
  }

  /**
   * Looks up one catalog entry.
   *
   * @param id The entry id, for example {@code mecab.ipadic}.
   * @return The entry. Never {@code null}.
   * @throws IOException Thrown if the entry is incomplete or invalid.
   * @throws IllegalArgumentException Thrown if {@code id} is {@code null}.
   */
  public Entry get(String id) throws IOException {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    final String url = properties.getProperty(id + URL_SUFFIX);
    final String sha512 = properties.getProperty(id + ".sha512");
    if (url == null || sha512 == null) {
      throw new IOException("unknown or incomplete dictionary catalog entry: " + id);
    }
    final String filename = properties.getProperty(id + ".filename");
    try {
      return new Entry(id, new URI(url), sha512.trim(), filename);
    } catch (URISyntaxException | IllegalArgumentException e) {
      throw new IOException("invalid dictionary catalog entry: " + id, e);
    }
  }

  /**
   * Installs a catalog entry into {@code targetDirectory} after checking that remote
   * catalog downloads are enabled. The entry is fetched, digest-verified, and unpacked
   * by {@link ResourceInstaller#install(URI, Path, String)}: an archive expands into
   * the directory, and a plain file is stored under its source name.
   *
   * @param id The entry id. Must not be {@code null}.
   * @param targetDirectory The directory to install into; created when absent. Must
   *                        not be {@code null}.
   * @throws IOException Thrown if the property is not enabled, the entry is missing,
   *         the download fails verification, or the target already contains an
   *         installed file.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public void install(String id, Path targetDirectory) throws IOException {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    if (targetDirectory == null) {
      throw new IllegalArgumentException("targetDirectory must not be null");
    }
    if (!Boolean.getBoolean(REMOTE_DOWNLOAD_PROPERTY)) {
      throw new IOException("remote dictionary catalog downloads are disabled; set -D"
          + REMOTE_DOWNLOAD_PROPERTY + "=true to enable");
    }
    final Entry entry = get(id);
    if (entry.filename() == null) {
      ResourceInstaller.install(entry.uri(), targetDirectory, entry.sha512());
    } else {
      ResourceInstaller.installNamed(
          entry.uri(), targetDirectory, entry.sha512(), entry.filename());
    }
  }

  /**
   * One catalog entry with a URI and the SHA-512 digest of its bytes.
   *
   * @param id The catalog id.
   * @param uri The absolute download URI.
   * @param sha512 The expected SHA-512 hex digest.
   * @param filename An optional preferred local file name; may be {@code null}.
   */
  public record Entry(String id, URI uri, String sha512, String filename) {
    /**
     * @param id The catalog id. Must not be {@code null}.
     * @param uri The absolute download URI. Must not be {@code null}.
     * @param sha512 The expected SHA-512 hex digest. Must not be {@code null}.
     * @param filename An optional preferred local file name; may be {@code null}.
     * @throws IllegalArgumentException Thrown if a required value is {@code null},
     *         {@code uri} is relative, {@code sha512} is not 128 hex digits, or
     *         {@code filename} is not a local file name.
     */
    public Entry {
      if (id == null) {
        throw new IllegalArgumentException("id must not be null");
      }
      if (uri == null) {
        throw new IllegalArgumentException("uri must not be null");
      }
      if (!uri.isAbsolute()) {
        throw new IllegalArgumentException("uri must be absolute");
      }
      if (sha512 == null) {
        throw new IllegalArgumentException("sha512 must not be null");
      }
      if (sha512.length() != SHA_512_HEX_LENGTH) {
        throw new IllegalArgumentException("sha512 must be 128 hex digits");
      }
      try {
        HexFormat.of().parseHex(sha512);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("sha512 must be 128 hex digits", e);
      }
      if (filename != null) {
        try {
          ResourceInstaller.validateSourceName(filename);
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("filename must be a file name", e);
        }
      }
    }
  }
}
