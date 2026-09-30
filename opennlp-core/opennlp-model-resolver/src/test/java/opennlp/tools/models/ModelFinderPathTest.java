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
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import opennlp.tools.models.dir.DirectoryModelFinder;
import opennlp.tools.models.simple.SimpleClassPathModelFinder;

class ModelFinderPathTest {

  private static final byte[] MODEL = {1, 2, 3};
  private static final byte[] PROPERTIES = {4, 5, 6};

  @TempDir
  private Path directory;

  private static Stream<Arguments> jarMasks() {
    return Stream.of(false, true).flatMap(simple -> Stream.of(
        Arguments.of(simple, "model space.jar", "model space.jar", true),
        Arguments.of(simple, "model space.jar", "model%20space.jar", false),
        Arguments.of(simple, "model-é.jar", "model-é.jar", true),
        Arguments.of(simple, "model-é.jar", "model-?.jar", true),
        Arguments.of(simple, "model-😀.jar", "model-😀.jar", true),
        Arguments.of(simple, "model-😀.jar", "model-?.jar", true),
        Arguments.of(simple, "model-😀.jar", "model-??.jar", false),
        Arguments.of(simple, "model-😀😀.jar", "model-?.jar", false),
        Arguments.of(simple, "model[1].jar", "model[1].jar", true),
        Arguments.of(simple, "model[1].jar", "model1.jar", false),
        Arguments.of(simple, "model+1.jar", "model+1.jar", true),
        Arguments.of(simple, "model+1.jar", "model 1.jar", false),
        Arguments.of(simple, "model%.jar", "model%.jar", true),
        Arguments.of(simple, "model%20.jar", "model%20.jar", true),
        Arguments.of(simple, "model%20.jar", "model .jar", false),
        Arguments.of(simple, "model%F0%9F%98%80.jar", "model😀.jar", false),
        Arguments.of(simple, "model#1.jar", "model#1.jar", true)));
  }

  @ParameterizedTest(name = "simple={0}, jar={1}, mask={2}, matches={3}")
  @MethodSource("jarMasks")
  void testJarMaskUsesFileName(boolean simple, String jarName, String mask, boolean matches)
      throws Exception {
    final Path jar = createJar(jarName, "nested/en.bin", "nested/model.properties");
    final Set<ClassPathModelEntry> models = findModels(simple, jar, mask);
    Assertions.assertEquals(matches ? 1 : 0, models.size());
    if (matches) {
      assertReadable(models.iterator().next(), "nested/en.bin", "nested/model.properties");
    }
  }

  private static Stream<Arguments> entryNames() {
    return Stream.of(false, true).flatMap(simple -> Stream.of(
        "my models", "café", "😀", "[models]", "models+1", "models%", "models%20",
        "models#1", "models?1", "models!/nested")
        .map(name -> Arguments.of(simple, name)));
  }

  @ParameterizedTest(name = "simple={0}, name={1}")
  @MethodSource("entryNames")
  void testJarEntryUrisRemainReadable(boolean simple, String name) throws Exception {
    final String entry = name + "/" + name.substring(name.lastIndexOf('/') + 1) + ".bin";
    final String properties = name + "/model.properties";
    final Path jar = createJar("model.jar", entry, properties);
    final Set<ClassPathModelEntry> models = findModels(simple, jar, "model.jar");
    Assertions.assertEquals(1, models.size());
    assertReadable(models.iterator().next(), entry, properties);
  }

  /**
   * Checks that a class loader URL with an unescaped space, which is not a valid URI, does not
   * stop the scan.
   */
  @Test
  void testUnescapedClassLoaderUrlDoesNotFail() throws Exception {
    final Path jar = createJar("model space.jar", "nested/en.bin", "nested/model.properties");
    final URL unescaped = UnescapedUrls.of(jar.toAbsolutePath().toString());
    final Thread thread = Thread.currentThread();
    final ClassLoader original = thread.getContextClassLoader();
    try (URLClassLoader loader = new URLClassLoader(new URL[] {unescaped}, original)) {
      thread.setContextClassLoader(loader);
      Assertions.assertDoesNotThrow(
          () -> new SimpleClassPathModelFinder("model space.jar").findModels(false));
    } finally {
      thread.setContextClassLoader(original);
    }
  }

  private Path createJar(String name, String entry, String properties) throws IOException {
    // file names outside the platform charset, e.g. under a POSIX locale, cannot be created
    Assumptions.assumeTrue(
        Charset.forName(System.getProperty("sun.jnu.encoding", "UTF-8")).newEncoder().canEncode(name),
        "the platform file name charset cannot encode " + name);
    final Path jar = directory.resolve(name);
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
      out.putNextEntry(new JarEntry(entry));
      out.write(MODEL);
      out.closeEntry();
      out.putNextEntry(new JarEntry(properties));
      out.write(PROPERTIES);
      out.closeEntry();
      out.putNextEntry(new JarEntry(entry + ".bak"));
      out.write(new byte[] {7});
      out.closeEntry();
    }
    return jar;
  }

  private Set<ClassPathModelEntry> findModels(boolean simple, Path jar, String mask) throws IOException {
    final Thread thread = Thread.currentThread();
    final ClassLoader original = thread.getContextClassLoader();
    try (URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, original)) {
      thread.setContextClassLoader(loader);
      final ClassPathModelFinder finder = simple
          ? new SimpleClassPathModelFinder(mask) : new DirectoryModelFinder(mask, directory, false);
      final Set<ClassPathModelEntry> models = finder.findModels(false);
      Assertions.assertEquals(models, finder.findModels(true));
      return models;
    } finally {
      thread.setContextClassLoader(original);
    }
  }

  private void assertReadable(ClassPathModelEntry model, String entry, String properties)
      throws IOException {
    assertResource(model.model(), entry, MODEL);
    Assertions.assertTrue(model.properties().isPresent());
    assertResource(model.properties().orElseThrow(), properties, PROPERTIES);
  }

  private void assertResource(URI uri, String name, byte[] expected) throws IOException {
    final URLConnection connection = uri.toURL().openConnection();
    connection.setUseCaches(false);
    Assertions.assertEquals(name, ((JarURLConnection) connection).getEntryName());
    try (InputStream in = connection.getInputStream()) {
      Assertions.assertArrayEquals(expected, in.readAllBytes());
    }
  }
}
