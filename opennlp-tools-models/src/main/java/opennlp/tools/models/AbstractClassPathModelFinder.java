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
package opennlp.tools.models;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import opennlp.tools.commons.ThreadSafe;

/**
 * A base implementation of a {@link ClassPathModelFinder} for the detection of
 * OpenNLP models in the classpath. By default, {@link AbstractClassPathModelFinder} will scan for
 * JAR files starting with "opennlp-models-*".
 * <p>
 * This search mask can be adjusted by using the one argument
 * {@link AbstractClassPathModelFinder#AbstractClassPathModelFinder(String) constructor}.
 * Wildcard search is supported by using asterisk symbol.
 * <p>
 * {@link #findModels(boolean)} may be called from several threads. Concurrent first calls scan
 * the classpath once, and every caller receives the complete, unmodifiable set of models.
 * Subclasses must keep {@link #getContext()} and {@link #getMatchingURIs(String, Object)} free
 * of shared mutable state.
 *
 * @see ClassPathModelFinder
 */
@ThreadSafe
public abstract class AbstractClassPathModelFinder implements ClassPathModelFinder {

  protected static final String JAR = "jar";

  private final String jarModelPrefix;
  private final Object scanLock = new Object();
  private volatile Set<ClassPathModelEntry> models;

  /**
   * By default, it scans for {@link #OPENNLP_MODEL_JAR_PREFIX}.
   */
  public AbstractClassPathModelFinder() {
    this(OPENNLP_MODEL_JAR_PREFIX);
  }

  /**
   * @param jarModelPrefix The leafnames of the jars that should be canned (e.g. "opennlp.jar").
   *                       May contain a wildcard glob ("opennlp-*.jar"). It must not be {@code null}.
   */
  public AbstractClassPathModelFinder(String jarModelPrefix) {
    Objects.requireNonNull(jarModelPrefix, "jarModelPrefix must not be null");
    this.jarModelPrefix = jarModelPrefix;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The result is cached; concurrent first calls scan the classpath once. The returned set
   * is unmodifiable and does not change when the cache is reloaded.</p>
   */
  @Override
  public Set<ClassPathModelEntry> findModels(boolean reloadCache) {
    Set<ClassPathModelEntry> found = this.models;
    if (found != null && !reloadCache) {
      return found;
    }
    synchronized (scanLock) {
      found = this.models;
      if (found == null || reloadCache) {
        found = scanModels();
        this.models = found;
      }
      return found;
    }
  }

  /**
   * Scans the classpath for models and pairs each model with the {@code model.properties} of
   * its jar.
   *
   * @return The unmodifiable set of model entries. It may be empty.
   */
  private Set<ClassPathModelEntry> scanModels() {
    final List<URI> classpathModels = getMatchingURIs("*.bin", getContext());
    final List<URI> classPathProperties = getMatchingURIs("model.properties", getContext());

    final Set<ClassPathModelEntry> found = new HashSet<>();
    for (URI model : classpathModels) {
      URI m = null;
      for (URI prop : classPathProperties) {
        if (jarPathsMatch(model, prop)) {
          m = prop;
          break;
        }
      }
      found.add(new ClassPathModelEntry(model, Optional.ofNullable(m)));
    }
    return Collections.unmodifiableSet(found);
  }

  /**
   * @apiNote Subclasses can implement this method to provide additional context to
   * {@link AbstractClassPathModelFinder#getMatchingURIs(String, Object)}.
   *
   * @return A context information object. May be {@code null}.
   */
  protected abstract Object getContext();

  /**
   * Retrieve matching classpath {@link URI URIs} for the given {@code wildcardPattern}.
   *
   * @param wildcardPattern The pattern to use for scanning. Must not be {@code null}.
   * @param context         An object holding context information. It might be {@code null}.
   * @return A list of matching classpath URIs.
   */
  protected abstract List<URI> getMatchingURIs(String wildcardPattern, Object context);

  protected boolean jarPathsMatch(URI uri1, URI uri2) {
    final String[] parts1 = parseJarURI(uri1);
    final String[] parts2 = parseJarURI(uri2);

    if (parts1 == null || parts2 == null) {
      return false;
    }

    return parts1[0].equals(parts2[0]);
  }

  protected String[] parseJarURI(URI uri) {
    if (JAR.equals(uri.getScheme())) {
      final String ssp = uri.getSchemeSpecificPart();
      final int separatorIndex = ssp.indexOf("!/");
      if (separatorIndex > 0) {
        final String jarFileUri = ssp.substring(0, separatorIndex);
        final String entryPath = ssp.substring(separatorIndex + 2);
        return new String[] {jarFileUri, entryPath};
      }
    }
    return null;
  }

  protected String getJarModelPrefix() {
    return jarModelPrefix;
  }

  /**
   * Escapes a {@code wildcard} expressions for usage as a Java regular expression.
   *
   * @param wildcard A valid expression. It must not be {@code null}.
   * @return The escaped regex.
   */
  protected String asRegex(String wildcard) {
    return wildcard
        .replace(".", "\\.")
        .replace("*", ".*")
        .replace("?", ".");
  }

  protected boolean matchesPattern(URL url, Pattern pattern) {
    return pattern.matcher(url.getFile()).matches();
  }

  /**
   * Converts a {@code location} in String form to a {@link URL}.
   *
   * @param location The resource path and/or reference.
   * @return The converted {@link URL} form.
   * @throws IOException Thrown if IO errors occurred during conversion
   */
  protected static URL toURL(String location) throws IOException {
    try {
      return new URI(location).toURL();
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  protected List<URI> getURIsFromJar(URL fileUrl, boolean isWindows) throws IOException {
    final List<URI> uris = new ArrayList<>();
    final String location = JAR + ":" +
        (isWindows ? fileUrl.toString().replace("\\", "/")
            : fileUrl.toString()) + "!/";
    final URL jarUrl = toURL(location);
    final JarURLConnection jarConnection = (JarURLConnection) jarUrl.openConnection();
    try (JarFile jarFile = jarConnection.getJarFile()) {
      final Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        final JarEntry entry = entries.nextElement();
        if (!entry.isDirectory()) {
          try {
            uris.add(new URI(jarUrl + entry.getName()));
          } catch (URISyntaxException ignored) {
            //if we cannot convert to URI here, we ignore that entry.
          }
        }
      }
    }

    return uris;
  }

  protected boolean isWindows() {
    return System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT).contains("win");
  }

}
