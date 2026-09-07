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
package opennlp.tools.models.dir;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import opennlp.tools.models.AbstractClassPathFinderTest;
import opennlp.tools.models.ClassPathModelEntry;
import opennlp.tools.models.ClassPathModelFinder;

/**
 * Runs the shared finder tests against a directory holding copies of the model jars
 * from the test classpath, placed one level below the scanned root.
 */
public class DirectoryModelFinderTest extends AbstractClassPathFinderTest {

  private static final String MODEL_JAR_PREFIX = "opennlp-models-";
  private static final String JAR_SUFFIX = ".jar";

  @TempDir
  private static Path root;
  private static Path modelDir;

  @BeforeAll
  static void copyModelJars() throws IOException, URISyntaxException {
    modelDir = Files.createDirectory(root.resolve("models"));
    final List<Path> jars = modelJarsOnClassPath();
    Assertions.assertFalse(jars.isEmpty(), "no model jars found on the test classpath");
    for (Path jar : jars) {
      Files.copy(jar, modelDir.resolve(jar.getFileName()));
    }
  }

  private static List<Path> modelJarsOnClassPath() throws URISyntaxException {
    final List<Path> jars = new ArrayList<>();
    final ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl instanceof URLClassLoader ucl) {
      for (URL url : ucl.getURLs()) {
        if ("file".equals(url.getProtocol())) {
          addIfModelJar(Path.of(url.toURI()), jars);
        }
      }
    }
    if (jars.isEmpty()) {
      for (String element : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
        addIfModelJar(Path.of(element), jars);
      }
    }
    return jars;
  }

  private static void addIfModelJar(Path candidate, List<Path> jars) {
    final String name = String.valueOf(candidate.getFileName());
    if (name.startsWith(MODEL_JAR_PREFIX) && name.endsWith(JAR_SUFFIX) && Files.isRegularFile(candidate)) {
      jars.add(candidate);
    }
  }

  @Override
  protected ClassPathModelFinder getModelFinder() {
    return new DirectoryModelFinder(null, root, true);
  }

  @Override
  protected ClassPathModelFinder getModelFinder(String pattern) {
    return new DirectoryModelFinder(pattern, root, true);
  }

  @Test
  void testNonRecursiveSkipsSubdirectories() {
    final Set<ClassPathModelEntry> models = new DirectoryModelFinder(null, root, false).findModels(false);
    Assertions.assertNotNull(models);
    Assertions.assertTrue(models.isEmpty());
  }

  @Test
  void testNonRecursiveFindsDirectChildren() {
    final Set<ClassPathModelEntry> models = new DirectoryModelFinder(null, modelDir, false).findModels(false);
    Assertions.assertEquals(4, models.size());
  }

  @Test
  void testJarPrefixNarrowsTheResult() {
    final Set<ClassPathModelEntry> pos =
        new DirectoryModelFinder("opennlp-models-pos-*", root, true).findModels(false);
    Assertions.assertEquals(1, pos.size());
    Assertions.assertTrue(pos.iterator().next().model().toString().contains("opennlp-models-pos-"));

    final Set<ClassPathModelEntry> none =
        new DirectoryModelFinder("opennlp-models-unknown-*", root, true).findModels(false);
    Assertions.assertTrue(none.isEmpty());
  }

  @Test
  void testNullDirectoryIsRejected() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> new DirectoryModelFinder(null, null, true));
  }
}
