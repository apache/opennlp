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
package opennlp.tools.stemmer.light;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks license metadata in the packaged runtime JAR. */
class LightStemmerLicenseIT {

  /**
   * Requires the Savoy license and standard Apache metadata in the runtime artifact.
   *
   * @throws IOException Thrown if reading the JAR fails.
   */
  @Test
  void testRuntimeJarIncludesSavoyLicense() throws IOException {
    final String path = System.getProperty("opennlp.runtimeJar");
    assertNotNull(path, "Maven must provide opennlp.runtimeJar");
    try (JarFile jar = new JarFile(path)) {
      assertNotNull(jar.getJarEntry("opennlp/tools/stemmer/light/GermanLightStemmer.class"));
      final String license = readEntry(jar, "META-INF/LICENSE");
      assertTrue(license.contains("Apache License"));
      assertTrue(license.contains("Copyright (c) 2005, Jacques Savoy"),
          "The runtime JAR must include the Savoy copyright");
      assertTrue(license.contains("Redistributions of source code must retain"));
      assertTrue(license.contains("Redistributions in binary form must reproduce"));
      assertTrue(license.contains("Neither the name of the author nor the names of its contributors"));
      assertTrue(license.contains("THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS"));
      assertTrue(license.contains("POSSIBILITY OF SUCH DAMAGE."));
      assertTrue(readEntry(jar, "META-INF/NOTICE").contains("The Apache Software Foundation"));
    }
  }

  /**
   * Loads an entry and joins trimmed lines for text checks.
   *
   * @param jar The runtime artifact.
   * @param name The entry path.
   * @return The entry text without line breaks.
   * @throws IOException Thrown if reading fails.
   */
  private String readEntry(JarFile jar, String name) throws IOException {
    final JarEntry entry = jar.getJarEntry(name);
    assertNotNull(entry, name);
    try (InputStream input = jar.getInputStream(entry)) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
          .map(String::strip).collect(Collectors.joining(" "));
    }
  }
}
