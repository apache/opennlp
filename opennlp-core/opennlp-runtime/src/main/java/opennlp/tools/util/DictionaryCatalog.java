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
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Opt-in catalog of remote dictionary archives and companion files. The catalog
 * ships URLs and SHA-512 digests only; it never bundles the data itself. Fetching
 * an entry requires {@link DownloadUtil#REMOTE_DOWNLOAD_PROPERTY} to be
 * {@code true}, so enabling a built-in URL is an explicit user action.
 *
 * @since 3.0.0
 */
public final class DictionaryCatalog {

  private static final String DEFAULT_RESOURCE =
      "opennlp/tools/util/dictionary-catalog.properties";

  private final Properties properties;

  private DictionaryCatalog(Properties properties) {
    this.properties = properties;
  }

  /**
   * Loads the catalog shipped on the classpath.
   *
   * @return The catalog. Never {@code null}.
   * @throws IOException Thrown if the resource is missing or cannot be read.
   */
  public static DictionaryCatalog loadDefault() throws IOException {
    try (InputStream in = DictionaryCatalog.class.getClassLoader()
        .getResourceAsStream(DEFAULT_RESOURCE)) {
      if (in == null) {
        throw new IOException("missing classpath resource " + DEFAULT_RESOURCE);
      }
      return load(in);
    }
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
   * {@return the catalog entry ids, in encounter order}
   */
  public Set<String> ids() {
    final Set<String> ids = new LinkedHashSet<>();
    for (final String key : properties.stringPropertyNames()) {
      if (key.endsWith(".url")) {
        ids.add(key.substring(0, key.length() - ".url".length()));
      }
    }
    return Collections.unmodifiableSet(ids);
  }

  /**
   * Looks up one catalog entry.
   *
   * @param id The entry id, for example {@code mecab.ipadic}.
   * @return The entry. Never {@code null}.
   * @throws IOException Thrown if the entry is incomplete or the URI is malformed.
   * @throws IllegalArgumentException Thrown if {@code id} is {@code null}.
   */
  public Entry get(String id) throws IOException {
    if (id == null) {
      throw new IllegalArgumentException("id must not be null");
    }
    final String url = properties.getProperty(id + ".url");
    final String sha512 = properties.getProperty(id + ".sha512");
    if (url == null || sha512 == null) {
      throw new IOException("unknown or incomplete dictionary catalog entry: " + id);
    }
    final String filename = properties.getProperty(id + ".filename");
    try {
      return new Entry(id, new URI(url), sha512.trim(), filename);
    } catch (URISyntaxException e) {
      throw new IOException("malformed catalog URI for " + id, e);
    }
  }

  /**
   * Downloads a catalog entry into {@code target} after checking that remote catalog
   * downloads are enabled.
   *
   * @param id The entry id. Must not be {@code null}.
   * @param target The local file to create. Must not be {@code null}.
   * @throws IOException Thrown if the property is not enabled, the entry is missing,
   *         or the download fails verification.
   * @throws IllegalArgumentException Thrown if a parameter is {@code null}.
   */
  public void download(String id, Path target) throws IOException {
    if (target == null) {
      throw new IllegalArgumentException("target must not be null");
    }
    if (!DownloadUtil.isRemoteDownloadEnabled()) {
      throw new IOException("remote dictionary catalog downloads are disabled; set -D"
          + DownloadUtil.REMOTE_DOWNLOAD_PROPERTY + "=true to enable");
    }
    final Entry entry = get(id);
    DownloadUtil.download(entry.uri(), target, entry.sha512());
  }

  /**
   * One pinned remote file: a stable URL and the SHA-512 of its bytes.
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
     */
    public Entry {
      if (id == null) {
        throw new IllegalArgumentException("id must not be null");
      }
      if (uri == null) {
        throw new IllegalArgumentException("uri must not be null");
      }
      if (sha512 == null) {
        throw new IllegalArgumentException("sha512 must not be null");
      }
    }
  }
}
