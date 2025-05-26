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

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A base implementation of a {@link ClassPathModelFinder} for the detection of
 * OpenNLP models in the classpath. By default, {@link AbstractClassPathModelFinder} will scan for
 * JAR files starting with "opennlp-models-*".
 * <p>
 * This search mask can be adjusted by using the one argument
 * {@link AbstractClassPathModelFinder#AbstractClassPathModelFinder(String) constructor}.
 * Wildcard search is supported by using asterisk symbol.
 *
 * @see ClassPathModelFinder
 */
public abstract class AbstractClassPathModelFinder implements ClassPathModelFinder {

  protected static final String JAR = "jar";

  private final String jarModelPrefix;
  private Set<ClassPathModelEntry> models;

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

  @Override
  public Set<ClassPathModelEntry> findModels(boolean reloadCache) {

    if (this.models == null || reloadCache) {
      final List<URI> classpathModels = getMatchingURIs("*.bin", getContext());
      final List<URI> classPathProperties = getMatchingURIs("model.properties", getContext());

      this.models = new HashSet<>();

      for (URI model : classpathModels) {
        URI m = null;
        for (URI prop : classPathProperties) {
          if (jarPathsMatch(model, prop)) {
            m = prop;
            break;
          }
        }
        this.models.add(new ClassPathModelEntry(model, Optional.ofNullable(m)));

      }
    }
    return this.models;
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

}
