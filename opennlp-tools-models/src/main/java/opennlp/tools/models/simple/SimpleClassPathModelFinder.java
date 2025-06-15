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
package opennlp.tools.models.simple;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import opennlp.tools.models.AbstractClassPathModelFinder;
import opennlp.tools.models.ClassPathModelFinder;

/**
 * Enables the detection of OpenNLP models in the classpath via JDK classes
 * By default, this class will search for JAR files starting with "opennlp-models-*".
 * This wildcard pattern can be adjusted by using the alternative constructor of this class.
 *   
 * @implNote
 * It is a rather simple implementation of scanning the classpath by trying to obtain {@link URL urls}
 * from the actual classpath via a chain of possible options. It might not work for every use-case
 * since it relies on JDK internals only and doesn't account for classloader hierarchies or edge-cases.
 * <p>
 * It will:
 * <ol>
 *  <li>Try to see if we have a {@link URLClassLoader} available in the current thread.</li>
 *  <li>Try to obtain URLs via the build in classloader via reflections.
 *  <br/>(requires {@code --add-opens java.base/jdk.internal.loader=ALL-UNNAMED} as JVM argument)</li>
 *  <li>Try to use the bootstrap classpath via {@code java.class.path}.</li>
 * </ol>
 *
 * <p>
 * If you need a more sophisticated implementation,
 * use {@link opennlp.tools.models.classgraph.ClassgraphModelFinder}.
 *
 * @see ClassPathModelFinder
 */
public class SimpleClassPathModelFinder extends AbstractClassPathModelFinder implements ClassPathModelFinder {

  private static final Logger logger = LoggerFactory.getLogger(SimpleClassPathModelFinder.class);
  private static final String FILE_PREFIX = "file";
  private static final Pattern CLASSPATH_SEPARATOR_PATTERN_WINDOWS = Pattern.compile(";");
  private static final Pattern CLASSPATH_SEPARATOR_PATTERN_UNIX = Pattern.compile(":");
  // ; for Windows, : for Linux/OSX

  /**
   * By default, it scans for {@link #OPENNLP_MODEL_JAR_PREFIX}.
   */
  public SimpleClassPathModelFinder() {
    this(OPENNLP_MODEL_JAR_PREFIX);
  }

  /**
   * @param modelJarPrefix The leafnames of the jars that should be canned (e.g. "opennlp.jar").
   *                       May contain a wildcard glob ("opennlp-*.jar"). It must not be {@code null}.
   */
  public SimpleClassPathModelFinder(String modelJarPrefix) {
    super(modelJarPrefix);
  }

  /**
   * @return Always {@code null} as it is not needed for the simple case.
   */
  @Override
  protected Object getContext() {
    return null; //not needed for the simple case. Just return NULL.
  }

  /**
   * @param wildcardPattern The pattern to use for scanning. Must not be {@code null}.
   * @param context         An object holding context information. It might be {@code null}.
   *                        It is unused within this implementation.
   * @return A list of matching classpath {@link URI URIs}. It may be an empty list if nothing is found.
   */
  @Override
  protected List<URI> getMatchingURIs(String wildcardPattern, Object context) {
    if (wildcardPattern == null) {
      return Collections.emptyList();
    }

    final boolean isWindows = isWindows();
    final List<URL> cp = getClassPathElements();
    final List<URI> cpu = new ArrayList<>();
    final Pattern jarPattern = Pattern.compile(asRegex("*" + getJarModelPrefix()));
    final Pattern filePattern = Pattern.compile(asRegex("*" + wildcardPattern));

    for (URL url : cp) {
      if (matchesPattern(url, jarPattern)) {
        try {
          for (URI u : getURIsFromJar(url, isWindows)) {
            if (matchesPattern(u.toURL(), filePattern)) {
              cpu.add(u);
            }
          }
        } catch (IOException e) {
          logger.warn("Cannot read content of {}.", url, e);
        }
      }
    }

    return cpu;
  }

  /**
   * Escapes a {@code wildcard} expressions for usage as a Java regular expression.
   *
   * @param wildcard A valid expression. It must not be {@code null}.
   * @return The escaped regex.
   */
  private String asRegex(String wildcard) {
    return wildcard
        .replace(".", "\\.")
        .replace("*", ".*")
        .replace("?", ".");
  }

  private boolean matchesPattern(URL url, Pattern pattern) {
    return pattern.matcher(url.getFile()).matches();
  }

  private List<URI> getURIsFromJar(URL fileUrl, boolean isWindows) throws IOException {
    final List<URI> uris = new ArrayList<>();
    final URL jarUrl = new URL(JAR + ":" +
        (isWindows ? fileUrl.toString().replace("\\", "/")
            : fileUrl.toString()) + "!/");
    final JarURLConnection jarConnection = (JarURLConnection) jarUrl.openConnection();
    try (JarFile jarFile = jarConnection.getJarFile()) {
      final Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        final JarEntry entry = entries.nextElement();
        if (!entry.isDirectory()) {
          final URL entryUrl = new URL(jarUrl + entry.getName());
          try {
            uris.add(entryUrl.toURI());
          } catch (URISyntaxException ignored) {
            //if we cannot convert to URI here, we ignore that entry.
          }
        }
      }
    }

    return uris;
  }

  private boolean isWindows() {
    return System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT).contains("win");
  }

  /**
   * Attempts to obtain {@link URL URLs} from the classpath in the following order:
   * <p>
   * <ol>
   *  <li>Try to see if we have a {@link URLClassLoader} available in the current thread.</li>
   *  <li>Try to obtain URLs via the build in classloader via reflections.
   *  <br/>(requires {@code --add-opens java.base/jdk.internal.loader=ALL-UNNAMED} as JVM argument)</li>
   *  <li>Try to use the bootstrap classpath via {@code java.class.path}.</li>
   * </ol>
   *
   * @return A list of {@link URL URLs} within the classpath.
   */
  private List<URL> getClassPathElements() {
    final ClassLoader cl = Thread.currentThread().getContextClassLoader();

    if (cl instanceof URLClassLoader ucl) {
      return Arrays.asList(ucl.getURLs());
    } else {
      final URL[] fromUcp = getURLs(cl);
      if (fromUcp != null && fromUcp.length > 0) {
        return Arrays.asList(fromUcp);
      } else {
        return getClassPathUrlsFromSystemProperty();
      }
    }
  }

  private List<URL> getClassPathUrlsFromSystemProperty() {
    final String cp = System.getProperty("java.class.path", "");
    final String[] matches = isWindows()
            ? CLASSPATH_SEPARATOR_PATTERN_WINDOWS.split(cp)
            : CLASSPATH_SEPARATOR_PATTERN_UNIX.split(cp);
    final List<URL> jarUrls = new ArrayList<>();
    for (String classPath: matches) {
      try {
        jarUrls.add(new URL(FILE_PREFIX, "", classPath));
      } catch (MalformedURLException ignored) {
        //if we cannot parse a URL from the system property, just ignore it...
        //we couldn't load it anyway
      }
    }
    return jarUrls;
  }

  /*
   * Java 9+ Bridge to obtain URLs from classpath.
   * This requires "--add-opens java.base/jdk.internal.loader=ALL-UNNAMED" as JVM argument
   */
  private URL[] getURLs(ClassLoader classLoader) {
    try {
      final Class<?> builtinClazzLoader = Class.forName("jdk.internal.loader.BuiltinClassLoader");

      final Field ucpField = builtinClazzLoader.getDeclaredField("ucp");
      ucpField.setAccessible(true);

      final Object ucpObject = ucpField.get(classLoader);
      final Class<?> clazz = Class.forName("jdk.internal.loader.URLClassPath");

      if (ucpObject != null) {
        final Method getURLs = clazz.getMethod("getURLs");

        return (URL[]) getURLs.invoke(ucpObject);
      }

    } catch (Exception ignored) {
      //ok here because we still have a fallback and this is just one step in the chain of possible
      //options to obtain URLs from the classpath
    }
    return new URL[0];
  }
}
